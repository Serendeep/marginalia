package com.serendeep.marginalia.update

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

/** A zstd patch that turns the APK of [fromVersionCode] into the full one. */
data class UpdatePatch(val fromVersionCode: Long, val url: String, val sha256: String, val size: Long)

/** One entry of the published update feed. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val notesUrl: String?,
    val publishedAt: String?,
    val patches: List<UpdatePatch> = emptyList(),
    val channel: String? = null,
) {
    /** The patch that applies to the installed version, if the feed has one. */
    fun patchFor(installed: Long): UpdatePatch? = patches.firstOrNull { it.fromVersionCode == installed }

    /** Bytes the next download will transfer, or -1 when unknown. */
    fun downloadSize(installed: Long, usePatch: Boolean = true): Long =
        patchFor(installed)?.takeIf { usePatch }?.size?.takeIf { it > 0 } ?: size

    fun toJson(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("apkUrl", apkUrl)
        .put("sha256", sha256)
        .put("size", size)
        .put("notesUrl", notesUrl ?: JSONObject.NULL)
        .put("publishedAt", publishedAt ?: JSONObject.NULL)
        .put("channel", channel ?: JSONObject.NULL)
        .put(
            "patches",
            JSONArray(
                patches.map {
                    JSONObject().put("fromVersionCode", it.fromVersionCode).put("url", it.url).put("sha256", it.sha256).put("size", it.size)
                },
            ),
        )
        .toString()

    companion object {
        /** Null when the feed lacks anything needed to download and check an APK. */
        fun parse(json: String): UpdateInfo? = try {
            val o = JSONObject(json)
            val code = o.optLong("versionCode", 0)
            val url = o.optString("apkUrl")
            val sha = o.optString("sha256").lowercase()
            if (code <= 0 || !isSafeUrl(url) || !SHA256_HEX.matches(sha)) {
                null
            } else {
                UpdateInfo(
                    versionCode = code,
                    versionName = o.optString("versionName").ifEmpty { code.toString() },
                    apkUrl = url,
                    sha256 = sha,
                    size = o.optLong("size", -1),
                    notesUrl = o.optString("notesUrl").takeIf { it.isNotEmpty() && isSafeUrl(it) },
                    publishedAt = o.optString("publishedAt").ifEmpty { null },
                    patches = parsePatches(o.optJSONArray("patches")),
                    channel = o.optString("channel").ifEmpty { null },
                )
            }
        } catch (_: Exception) {
            null
        }

        /** Entries that cannot be downloaded and checked are dropped; the full APK still works without them. */
        private fun parsePatches(array: JSONArray?): List<UpdatePatch> = (0 until (array?.length() ?: 0)).mapNotNull { i ->
            val p = array?.optJSONObject(i) ?: return@mapNotNull null
            val from = p.optLong("fromVersionCode", 0)
            val url = p.optString("url")
            val sha = p.optString("sha256").lowercase()
            if (from <= 0 || !isSafeUrl(url) || !SHA256_HEX.matches(sha)) null else UpdatePatch(from, url, sha, p.optLong("size", -1))
        }

        private val SHA256_HEX = Regex("[0-9a-f]{64}")
        private val LOOPBACK = setOf("localhost", "127.0.0.1")

        /** https anywhere; plain http only to the local machine, for testing a feed on a device. */
        internal fun isSafeUrl(url: String): Boolean {
            val uri = try { URI(url) } catch (_: Exception) { return false }
            return uri.scheme == "https" || (uri.scheme == "http" && uri.host in LOOPBACK)
        }
    }
}

/** Never offers the installed version or anything older. */
fun isNewer(candidate: Long, installed: Long): Boolean = candidate > installed
