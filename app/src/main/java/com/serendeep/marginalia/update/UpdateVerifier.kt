package com.serendeep.marginalia.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import com.serendeep.marginalia.BuildConfig
import java.io.File
import java.security.MessageDigest

/** What the checks need to know, as plain values. */
data class ApkFacts(
    val sha256: String,
    val packageName: String?,
    val versionCode: Long,
    val signerSha256: Set<String>,
)

object UpdateVerifier {

    /** A human-readable reason the file must not be installed, or null when it passes. */
    fun problem(
        facts: ApkFacts,
        expectedSha256: String,
        ownPackage: String,
        installedVersionCode: Long,
        installedSignerSha256: Set<String>,
        pinnedSignerSha256: String,
    ): String? = when {
        !facts.sha256.equals(expectedSha256, ignoreCase = true) -> "The download is corrupt (checksum mismatch)"
        facts.packageName == null -> "The downloaded file is not a readable app package"
        facts.packageName != ownPackage -> "The download is for a different app (${facts.packageName})"
        !isNewer(facts.versionCode, installedVersionCode) -> "The download is not newer than the installed version"
        facts.signerSha256.isEmpty() -> "The download has no readable signature"
        pinnedSignerSha256.isNotEmpty() && facts.signerSha256 != setOf(pinnedSignerSha256.lowercase()) ->
            "The download is not signed with the Marginalia release key"
        installedSignerSha256.isNotEmpty() && facts.signerSha256 != installedSignerSha256 ->
            "The download is signed with a different key than the installed app"
        else -> null
    }

    /** Reads [file] and returns [problem] for it. */
    fun verify(context: Context, file: File, expectedSha256: String): String? {
        val pm = context.packageManager
        val own = context.packageName
        val installed = pm.signersOf(pm.getPackageInfo(own, PackageManager.GET_SIGNING_CERTIFICATES))
        val archive = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
        val facts = ApkFacts(
            sha256 = sha256Of(file),
            packageName = archive?.packageName,
            versionCode = archive?.longVersionCode ?: 0,
            signerSha256 = archive?.let { pm.signersOf(it) }.orEmpty(),
        )
        return problem(
            facts,
            expectedSha256,
            own,
            pm.getPackageInfo(own, 0).longVersionCode,
            installed,
            BuildConfig.UPDATE_CERT_SHA256,
        )
    }

    private fun PackageManager.signersOf(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        // Only the current signer counts; a rotated lineage's older keys are history.
        val signers: Array<Signature> = signing.apkContentsSigners ?: return emptySet()
        return signers.map { certSha256(it) }.toSet()
    }

    internal fun certSha256(signature: Signature): String =
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
}
