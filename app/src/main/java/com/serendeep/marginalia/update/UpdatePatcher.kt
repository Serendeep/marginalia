package com.serendeep.marginalia.update

import com.github.luben.zstd.ZstdInputStream
import java.io.File
import java.io.IOException

// `zstd --patch-from` frames are made with --long=27, so the decoder has to accept that window.
private const val WINDOW_LOG_MAX = 27

internal object UpdatePatcher {

    /**
     * Rebuilds the new APK into [out] from [old] and a `zstd --patch-from=old` [patch], and checks it
     * against [expectedSha256]. [out] is removed on any failure. The old file is the prefix dictionary,
     * so it is held in memory for the length of the decode.
     */
    fun apply(old: File, patch: File, out: File, expectedSha256: String) {
        try {
            val dictionary = old.readBytes()
            ZstdInputStream(patch.inputStream().buffered()).use { input ->
                input.setDict(dictionary).setLongMax(WINDOW_LOG_MAX)
                out.outputStream().buffered().use { input.copyTo(it) }
            }
            if (!sha256Of(out).equals(expectedSha256, ignoreCase = true)) throw IOException("The patched update does not match its checksum")
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
    }
}
