# Self-update for a sideloaded Marginalia (GitHub Releases)

Researched 2026-10-09. Primary sources only; "measured" means I ran it on the real release APKs. Claims I could not verify are marked UNVERIFIED.

## 0. Findings that change the plan
- The shipped APK is not 29 MB. v0.4.0 = 29.5 MB, but v0.5.0 and v1.0.0 = 60.5 MB (they added `libdigitalink.so`; 3 extra ABIs ship). Measured: dropping x86, x86_64 and armeabi-v7a native libs leaves ~21 MB. An arm64-only APK is the cheapest "delta" there is (about -65%).
- Measured zstd patch (`zstd -19 --long=27 --patch-from=old new`, round-trip byte-identical): v0.5.0 -> v1.0.0 (near-identical build) = 1.05 MB (1.7% of 60.5 MB). v0.4.0 -> v1.0.0 (new native libs) = 11.5 MB. Plain zstd of the full APK = 23.3 MB (APK is already largely deflated/stored).
- On AOSP, a self-update can skip the confirm dialog even when the first copy came from adb (see 2.2: `|| isSelfUpdate`). Huawei behavior is UNVERIFIED and needs a device test.

## 1. How established apps self-update
**Signal website build** (signalapp/Signal-Android, `app/src/main/java/org/thoughtcrime/securesms/`):
- `jobs/ApkUpdateJob.kt` https://github.com/signalapp/Signal-Android/blob/main/app/src/main/java/org/thoughtcrime/securesms/jobs/ApkUpdateJob.kt
  - Version source: a JSON manifest at `BuildConfig.APK_UPDATE_MANIFEST_URL` = `{versionCode, versionName, url, sha256sum, uploadTimestamp}`. Update iff `versionCode` is greater than the installed one.
  - Network constraint, `setMaxAttempts(2)`. Download uses the system `DownloadManager` (Wi-Fi only, file in `getExternalFilesDir`, `signal-update.apk`), chosen "for easy reliability" for a 70 MB file. Pending download id + digest are persisted so a re-check does not re-download.
- `apkupdate/ApkUpdateInstaller.kt` https://github.com/signalapp/Signal-Android/blob/main/app/src/main/java/org/thoughtcrime/securesms/apkupdate/ApkUpdateInstaller.kt
  - Re-hashes the downloaded file and compares with `MessageDigest.isEqual` before install. There is no explicit signing-cert check; it relies on the OS rejecting a different signer and on `setAppPackageName(context.packageName)`.
  - `PackageInstaller.SessionParams(MODE_FULL_INSTALL)`, `setAppPackageName`, `setRequireUserAction(USER_ACTION_NOT_REQUIRED)` on API >= 31, `openWrite` -> copy -> `commit(PendingIntent broadcast)`.
  - Notable: `shouldAutoUpdate()` is hard-coded `return false` (silent path commented out, reason given: no opt-out UI yet). Signal always posts an "install" notification; the tap re-enters with `userInitiated=true`. The silent design was: API >= 31, setting on, app NOT foregrounded, no active call.
- `apkupdate/ApkUpdatePackageInstallerReceiver.kt`: handles `STATUS_SUCCESS`, `STATUS_PENDING_USER_ACTION` (starts `EXTRA_INTENT` only if user initiated, else shows a prompt notification), and each `STATUS_FAILURE_*` -> a reason enum.

**Mihon (Tachiyomi fork)** https://github.com/mihonapp/mihon
- `domain/.../release/interactor/GetApplicationRelease.kt` + `data/.../release/ReleaseServiceImpl.kt`: plain `GET https://api.github.com/repos/{repo}/releases/latest`, picks the asset by ABI name, compares dotted version numbers. Stable and nightly are separate repos (`mihon` / `mihon-preview`).
- App updates are handed to the download + system installer UI (no silent path). Extensions use `extension/installer/PackageInstallerInstaller.kt`: session + `USER_ACTION_NOT_REQUIRED` on API >= 31 + a receiver that launches `EXTRA_INTENT` on `STATUS_PENDING_USER_ACTION`. I did not locate a separate app-APK download job in the tree; treat the app-APK flow as UNVERIFIED.

**Obtainium** https://github.com/ImranR98/Obtainium (`lib/installers/stock_installer.dart`)
- `canInstallSilently` is true only if: installing package == Obtainium, SDK >= 31, and the target app's `targetSdk >= sdkInt - 3`; it explicitly refuses to silently update itself. Cites the `setRequireUserAction` javadoc. Good independent confirmation of the 2.2 rules.

**NewPipe** https://github.com/TeamNewPipe/NewPipe/blob/dev/app/src/main/java/org/schabi/newpipe/NewVersionWorker.kt
- Worker polls `https://newpipe.net/api/data.json` (not GitHub, to save API requests), checks version code and signing key match, then `ACTION_VIEW` on the APK URL (browser download; no PackageInstaller).

**Telegram APK, F-Droid client**: not read; UNVERIFIED. (F-Droid's privileged-extension path needs system privileges and is irrelevant for us.)

Pattern across all of them: tiny version manifest -> verify digest -> `PackageInstaller` session -> status receiver -> notification fallback.

## 2. Android mechanics
### 2.1 Session flow
`createSession(SessionParams)` -> `openSession` -> `openWrite(name, 0, sizeBytes)` -> copy -> `session.fsync(out)` -> `commit(IntentSender)`. Receiver gets `EXTRA_STATUS`; on `STATUS_PENDING_USER_ACTION` start `EXTRA_INTENT` (with `FLAG_ACTIVITY_NEW_TASK`). Use a mutable PendingIntent, as in Signal. API reference: https://developer.android.com/reference/android/content/pm/PackageInstaller (the web page is very long; I verified from the AOSP source below instead).
- Manifest: `REQUEST_INSTALL_PACKAGES` (user must flip "Install unknown apps" for Marginalia once; deep link `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with `package:` URI, check `packageManager.canRequestPackageInstalls()`).
- Marginalia today declares only `INTERNET` (`app/src/main/AndroidManifest.xml`). Must add `REQUEST_INSTALL_PACKAGES` and `UPDATE_PACKAGES_WITHOUT_USER_ACTION`.

### 2.2 When is the dialog skipped? (AOSP android15-release source)
`PackageInstaller.SessionParams#setRequireUserAction` javadoc https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/content/pm/PackageInstaller.java and logic in `PackageInstallerSession.java` (`computeUserActionRequirement`) https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/services/core/java/com/android/server/pm/PackageInstallerSession.java
No prompt requires ALL of:
1. `requireUserAction = USER_ACTION_NOT_REQUIRED`, installer has `UPDATE_PACKAGES_WITHOUT_USER_ACTION` (normal permission, auto-granted; `core/res/AndroidManifest.xml`).
2. Installer is (a) the update owner if ownership enforcement is on, else (b) the installer of record of the existing app, OR (c) updating itself. Code: `((isUpdateOwnershipEnforcementEnabled ? isUpdateOwner : isInstallerOfRecord) || isSelfUpdate)`. Because of (c), a self-update does not need us to be the installer of record, so the adb-installed first copy should also update silently on stock AOSP. Obtainium's refusal is only for its own different-app case.
3. App being installed targets >= a floor that rises with OS: API 29 on Android 12, 30 on 13, 31 on 14, 33 on 15. We target 35: fine for years.
4. Not throttled: the same installer silently updating the same package again within 30 s falls back to a prompt (`SilentUpdatePolicy`, 30 s).
5. Unknown-sources not disabled for us (else `USER_ACTION_REQUIRED` regardless).
Javadoc note: "Session owners should always be prepared to handle STATUS_PENDING_USER_ACTION." Keep that fallback.
- Android 14 update ownership: `setRequestUpdateOwnership(true)` (permission `ENFORCE_UPDATE_OWNERSHIP`, normal) only works at initial install and only matters on API 34+. If set at first install by another installer, we'd get an "update owner reminder" prompt. Our device is API 31, so: not applicable now; do not set it. (Side effect worth knowing: with ownership enforced, only the owner updates silently.)

### 2.3 Verify before install
- Hash: GitHub's asset API may expose a `digest` field (UNVERIFIED); CI already uploads `app-release.apk.sha256` and puts the digest in release notes. Stream-hash the staged file while downloading.
- Signature: `packageManager.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)` -> `signingInfo.apkContentsSigners` (null/multi-signer caveats in `SigningInfo.java`: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android15-release/core/java/android/content/pm/SigningInfo.java ). Compare SHA-256 of the cert against a pinned constant compiled into the app (the same value CI already publishes). Also check `packageName` and `longVersionCode > installed` (downgrade guard). I did not find a javadoc statement that signing info is returned for archives; it is widely used, but test on the device (UNVERIFIED on Huawei).
- Honest threat model: sha256 fetched from the same GitHub release only catches corruption/truncation. The pinned cert check plus the OS's own same-signer rule is what stops a tampered APK. A signature mismatch would already fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; our check just fails earlier with a clear message.

### 2.4 Huawei / EMUI
I found no primary or reputable report of EMUI interfering with `PackageInstaller` sessions specifically (searches returned only generic "install unknown apps" per-app toggle guides, e.g. https://www.techbone.net/huawei/smartphone/install-apps-from-external-sources). The AppGallery risk-check you see on adb installs is a package-verifier hook; whether it fires for a session commit from an app is UNVERIFIED. Budget one on-device experiment (Phase 1, step 0) and design for it: always handle `STATUS_PENDING_USER_ACTION`, and treat "silent" as best effort, never a guarantee. microG does not matter to the installer path.

## 3. UX patterns and GitHub API
- Check: app-start check (throttled to once per ~6 h) plus a `PeriodicWorkRequest` (min interval 15 min per https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work ; use 12-24 h, `NetworkType.UNMETERED`, `setRequiresBatteryNotLow`). Periodic runs can be delayed or skipped when constraints are not met.
- Download: write to `getExternalFilesDir(null)/update/<ver>.apk.part`, resume with `Range: bytes=N-` (+ `If-Range: <etag>`), verify then rename to `.apk`. GitHub asset URLs 302 to a CDN that supports ranges (UNVERIFIED for every region; test). At 64 KB/s, 21 MB ~ 5.5 min, 60 MB ~ 16 min, so resumability and Wi-Fi-only are mandatory. Alternative: system `DownloadManager` (Signal's choice) gives resume and retry for free but little control over staging; recommend our own OkHttp + WorkManager `CoroutineWorker` with `setForeground` for the long download.
- Install timing: never mid-ink. Offer "Update ready - restart to update" in settings/home; auto-install only when app is backgrounded (ProcessLifecycleOwner), no pen stroke in last N min, and notebook saved. Installing replaces the process, so flush the ink/DB state before `commit` (and hold until the receiver confirms).
- GitHub REST (https://docs.github.com/en/rest/releases/releases): `GET /repos/Serendeep/marginalia/releases/latest` returns the newest non-prerelease, non-draft release ("sorted by created_at"). For beta use `GET /repos/{o}/{r}/releases?per_page=5` and pick the first with `prerelease == true`. Asset `browser_download_url` is public (no auth, ok for a public repo). Unauthenticated limit: 60 requests/hour per IP (https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api). Conditional requests: ETag + `If-None-Match` returning 304 do not count against the primary limit, but the docs state this "while correctly authorized" (https://docs.github.com/en/rest/using-the-rest-api/best-practices-for-using-the-rest-api), so for anonymous clients assume 304s may still count. With 1-4 checks/day this is irrelevant; still send the ETag to save bytes.
- Alternative to API: host a static `latest.json` (Signal/NewPipe style) on GitHub Pages or the repo raw URL, written by CI. Cheaper than the API and gives room for per-version metadata (min version, delta URLs, kill switches).

## 4. Delta updates
- Options: bsdiff/bspatch (https://github.com/mendsley/bsdiff; slow, high RAM on 60 MB inputs), `zstd --patch-from` (https://github.com/facebook/zstd/blob/dev/programs/zstd.1.md ; needs `--long=27`; decoder also needs `--long=27` and ~old-file-size memory), Google archive-patcher "file-by-file" (https://github.com/google/archive-patcher ; ARCHIVED 2023-04, Java, handles zip recompression so deflated entries diff well). Google's published numbers for Play: bsdiff ~47% smaller on average, file-by-file ~65% smaller on average, >90% in some cases (https://android-developers.googleblog.com/2016/12/saving-data-reducing-the-size-of-app-updates-by-65-percent.html).
- Signature caveat: the patch must reproduce the new APK byte-for-byte. That is automatic for byte-level patchers (zstd/bsdiff): output hash == published sha256 and the v2/v3 signature stays valid. Verify sha256 of the output before install; on mismatch or any error fall back to the full APK. archive-patcher also reproduces the exact bytes (it recompresses with the recorded deflate settings; fails over if they do not match).
- Measured here (zstd, level 19): near-identical build 1.05 MB; build adding native libs 11.5 MB. Expect ~1-3 MB for typical code-only releases of a 60 MB APK, and ~0.5-1.5 MB if arm64-only (smaller base).
- CI: for each release, download the previous N (say 2) release APKs, run `zstd --patch-from`, attach `app-release-from-<prev>.zst`, list them in `latest.json` with their sha256 and the base version they apply to. Device: has the installed APK at `applicationInfo.sourceDir` (readable by the app; the base APK of the currently installed version), applies patch in a streaming JNI/`zstd-jni` decode (adds ~1 MB native lib, or a Java port) -> staged APK -> verify sha256. Caveat: installed `sourceDir` APK equals the released bytes only for non-split, non-re-signed installs; adb/sideload installs are verbatim, and the sha256 check guards the rest. Base version must be the one installed; skip the delta when a user is multiple versions behind.
- Priority order by payoff/effort: (1) arm64-only ABI filter (-65%, zero client code), (2) resumable download, (3) delta patches.

## 5. Hot patching reality check
- Tinker (https://github.com/Tencent/tinker): still active (latest v1.9.15.2, 2025-07-07; pushed 2026-09). README known issues: cannot update AndroidManifest.xml (so no new components), some Samsung API 21 devices unsupported, and it cannot be used for Google Play distribution (Play's developer distribution agreement). Needs build-plugin integration and a custom `Application`, which conflicts with Hilt/Compose-style app setup; R8 requires mapping/keep-rule discipline between base and patch builds.
- Robust (https://github.com/Meituan-Dianping/Robust): last push 2022-04. AndFix (https://github.com/alibaba/AndFix): last push 2020-11, Dalvik/early-ART hooks, dead on modern ART. Sophix is a closed Alibaba cloud product: UNVERIFIED, not read.
- Android 14: apps targeting 34+ that load dex/jar/apk dynamically must mark those files read-only before content is written or the system throws (https://developer.android.com/about/versions/14/behavior-changes-14, "Safer dynamic code loading"). We target 35, so any DCL patcher needs that. Android also discourages DCL in general (https://developer.android.com/privacy-and-security/security-tips#DynamicCode).
- Technical friction: R8 renames/inlines/removes code (patch classes must match obfuscated names); baseline profiles/ART AOT compiled code is bypassed (patched code runs interpreted/JIT = slower); Compose compiler generates synthetic classes and lambda keys that change across builds; the patch pipeline is itself a signed-code-execution channel that must be authenticated. For a single-user app this is a large attack surface and maintenance cost for little gain.
- Verdict: do NOT build code hot-patching. A fast, small, resumable full/delta update gives the same outcome (fixed behavior) with the normal OS security model.
- What does work as a "hotfix without reinstall": a signed-by-source JSON served from the repo (`remote-config.json` in the release or Pages), fetched with the update check, holding: kill switches / feature flags for risky features, AI model names and default prompts, rate/timeouts, a "minimum supported version" and a "known-bad versions -> force update" list, and an update-channel pointer. Constraints: it can only change what the app already reads from config, so design risky features behind flags up front. Validate with a schema + safe defaults; cache last-good; fail closed to bundled defaults.

## 6. Recommendation and phased plan
Effort estimates: "Agent" = Claude Code wall-clock including a test run on the tablet; "Human" = a developer working alone.

**Phase 0 (pre-work, do first)**: add `abiFilters "arm64-v8a"` (or ABI splits) for the release APK (arm64-only if the only fleet is the MatePad, add armeabi-v7a if old devices matter); have CI emit `latest.json`; run the Huawei experiment: install v-old by adb, call a throwaway session commit and note whether any dialog, Huawei risk screen or `STATUS_FAILURE_*` appears. Agent 1-2 h, Human 0.5 day.

**Phase 1 - core updater** (Agent ~4-6 h, ~900 LOC + tests; Human 2-3 days)
1. Manifest: `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`; settings screen: auto-check toggle, "Wi-Fi only", channel stable/beta (beta = `prerelease==true`), "Install automatically when idle", "Check now", last-checked, and the install-unknown-apps shortcut with `canRequestPackageInstalls()` gating.
2. `UpdateChecker`: reads `latest.json` (or `/releases/latest` as fallback), compares `versionCode`, honours the channel, never offers a downgrade.
3. `UpdateDownloadWorker` (CoroutineWorker, foreground-service notification, Range resume, `.part` file, streaming SHA-256, delete stale files).
4. `UpdateVerifier`: sha256 match, `getPackageArchiveInfo(GET_SIGNING_CERTIFICATES)` -> pinned cert, packageName, `longVersionCode` greater than current.
5. `UpdateInstaller`: session with `setAppPackageName`, `USER_ACTION_NOT_REQUIRED` (API 31+), receiver (`STATUS_*` mapping, pending-user-action -> notification, 30 s throttle awareness), abandon stale sessions, flush notebook state before commit.
6. Idle/backgrounded gate and a "Restart to update" row; a pen-active guard.
7. Tests: unit tests for version/channel logic, resume logic against a local MockWebServer, verifier against fixture APKs.

**Phase 2 - remote config / kill switches** (Agent ~2-3 h; Human ~1 day): `remote-config.json` in the release, schema-validated, cached, bundled defaults, flags consulted by the AI and risky features, "force update" if current version in `knownBad`.

**Phase 3 - delta updates** (Agent ~5-7 h; Human 2-4 days): CI step generating `--patch-from` files for the last 2 releases plus `latest.json` entries; on-device zstd decoder (zstd-jni or a small Java port), apply in a worker against `sourceDir`, verify output sha256, automatic full-APK fallback; measure and report the savings. Only worth it after Phase 0's ABI cut if real deltas still exceed ~3 MB.

**Not recommended**: code hot-patching (Tinker/Robust/AndFix/DCL) - see section 5.

### Risks
- Huawei risk-check or package-verifier dialog may appear on session installs or block silent installs (UNVERIFIED); first update may need one tap. Mitigation: pending-user-action fallback with a clear notification.
- User must grant "install unknown apps" once for Marginalia; without it the session always prompts or fails.
- Silent updates restart the process; a mid-stroke install loses unsaved ink unless state is flushed first.
- Signing key loss = no update path at all (new key requires uninstall, losing local data) unless you use APK Signature Scheme v3 key rotation with a lineage; back up the keystore and publish its fingerprint (already in release notes).
- Downgrade/rollback: OS refuses lower `versionCode`; also guard in-app. A broken release is fixed only by a higher version, so keep the kill-switch channel.
- GitHub dependency: rate limits, CDN range support, availability. Mitigation: `latest.json` on Pages, ETag, exponential backoff.
- Supply chain: pinned cert check is the real protection; do not trust the sha256 from the same origin.
- Throttle: back-to-back silent updates inside 30 s degrade to a prompt (only matters for testing).
