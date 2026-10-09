package com.serendeep.marginalia.update

import org.json.JSONObject
import java.net.URI

/** One entry of the published update feed. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val size: Long,
    val notesUrl: String?,
    val publishedAt: String?,
) {
    fun toJson(): String = JSONObject()
        .put("versionCode", versionCode)
        .put("versionName", versionName)
        .put("apkUrl", apkUrl)
        .put("sha256", sha256)
        .put("size", size)
        .put("notesUrl", notesUrl ?: JSONObject.NULL)
        .put("publishedAt", publishedAt ?: JSONObject.NULL)
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
                )
            }
        } catch (_: Exception) {
            null
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
