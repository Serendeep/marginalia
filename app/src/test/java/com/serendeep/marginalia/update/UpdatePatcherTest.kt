package com.serendeep.marginalia.update

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class UpdatePatcherTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun resource(name: String): File =
        File(tmp.root, name).also { it.writeBytes(javaClass.getResourceAsStream("/update/$name")!!.readBytes()) }

    private val old get() = resource("old.bin")
    private val patch get() = resource("patch.zst")
    private val newSha get() = sha256Of(resource("new.bin"))

    @Test
    fun rebuildsTheNewFileFromTheOldOneAndAZstdPatch() {
        val out = File(tmp.root, "out.apk")
        UpdatePatcher.apply(old, patch, out, newSha)
        assertArrayEquals(resource("new.bin").readBytes(), out.readBytes())
    }

    @Test
    fun aWrongChecksumLeavesNothingBehind() {
        val out = File(tmp.root, "out.apk")
        assertThrows(IOException::class.java) { UpdatePatcher.apply(old, patch, out, "0".repeat(64)) }
        assertFalse(out.exists())
    }

    @Test
    fun aPatchAgainstAnotherBaseIsRejected() {
        val other = File(tmp.root, "other.bin").apply { writeBytes(ByteArray(100_000) { (it * 31).toByte() }) }
        val out = File(tmp.root, "out.apk")
        assertThrows(Exception::class.java) { UpdatePatcher.apply(other, patch, out, newSha) }
        assertFalse(out.exists())
    }

    @Test
    fun aCorruptPatchIsRejected() {
        val bad = patch.apply { writeBytes(readBytes().also { it[it.size / 2] = (it[it.size / 2] + 1).toByte() }) }
        val out = File(tmp.root, "out.apk")
        assertThrows(Exception::class.java) { UpdatePatcher.apply(old, bad, out, newSha) }
        assertFalse(out.exists())
    }
}
