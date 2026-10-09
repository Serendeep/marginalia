package com.serendeep.marginalia.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVerifierTest {
    private val sha = "b".repeat(64)
    private val pinned = "c".repeat(64)
    private val own = "com.serendeep.marginalia"
    private val good = ApkFacts(sha, own, 8, setOf(pinned))

    private fun problem(
        facts: ApkFacts = good,
        expected: String = sha,
        installedCode: Long = 7,
        installedSigners: Set<String> = setOf(pinned),
        pin: String = pinned,
    ) = UpdateVerifier.problem(facts, expected, own, installedCode, installedSigners, pin)

    @Test
    fun acceptsAMatchingNewerApk() {
        assertNull(problem())
    }

    @Test
    fun checksumMismatchIsReported() {
        assertTrue(problem(expected = "d".repeat(64))!!.contains("checksum"))
    }

    @Test
    fun checksumComparisonIgnoresCase() {
        assertNull(problem(expected = sha.uppercase()))
    }

    @Test
    fun otherPackageIsRejected() {
        assertTrue(problem(good.copy(packageName = "evil.app"))!!.contains("different app"))
        assertNotNull(problem(good.copy(packageName = null)))
    }

    @Test
    fun equalOrOlderVersionIsRejected() {
        assertNotNull(problem(good.copy(versionCode = 7)))
        assertNotNull(problem(good.copy(versionCode = 6)))
    }

    @Test
    fun unsignedOrWrongSignerIsRejected() {
        assertNotNull(problem(good.copy(signerSha256 = emptySet())))
        assertTrue(problem(good.copy(signerSha256 = setOf("e".repeat(64))))!!.contains("release key"))
        assertNotNull(problem(good.copy(signerSha256 = setOf(pinned, "e".repeat(64)))))
    }

    @Test
    fun withoutAPinTheInstalledSignerDecides() {
        val demo = "f".repeat(64)
        assertNull(problem(good.copy(signerSha256 = setOf(demo)), installedSigners = setOf(demo), pin = ""))
        assertEquals(
            "The download is signed with a different key than the installed app",
            problem(good.copy(signerSha256 = setOf(pinned)), installedSigners = setOf(demo), pin = ""),
        )
    }

    @Test
    fun pinAndInstalledSignerMustBothHold() {
        val other = "f".repeat(64)
        assertNotNull(problem(installedSigners = setOf(other)))
    }
}
