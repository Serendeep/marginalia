# Contributing to Marginalia

Marginalia is a pen-first Android app for margin notes on lecture PDFs,
built with Jetpack Compose. Small fixes can go straight to a PR; for
anything bigger, open an issue first so nobody builds the same thing twice.

## Building

- JDK 17, Android Studio (or plain Gradle)
- Kotlin 2.0.21 · AGP 8.9.3 · compileSdk 36 · minSdk 29
- `./gradlew :app:assembleDebug` builds; `./gradlew :app:installDebug` installs.

Fonts are bundled in `res/font` so the app renders correctly on devices
without Google Play Services.

## Testing

- Unit tests: `./gradlew test`
- Connected tests: `./gradlew connectedDebugAndroidTest`, **emulator only.**
  The connected suite uninstalls the app after each run, which **deletes all
  app data**. Never run it against a device you actually take notes on.

## Pull requests

- Keep commits small and logical: one coherent change per commit.
- Commit messages: single-line conventional commits
  (`feat: …`, `fix: …`, `refactor: …`, `docs: …`, `perf: …`). No bodies.
- Match the surrounding code style; keep comments minimal and self-contained.
- If your change is user-visible, include a before/after screenshot in the PR.
- Check [ROADMAP.md](ROADMAP.md) for planned work before starting something big.

## License

By contributing you agree that your contributions are licensed under the
[GPL-3.0](LICENSE).
