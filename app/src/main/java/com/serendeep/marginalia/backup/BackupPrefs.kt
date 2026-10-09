package com.serendeep.marginalia.backup

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Settings that travel with a backup. Secrets and per-device state (update feed, backup schedule)
 * are never written out and never overwritten on restore.
 */
object BackupPrefs {
    private val excludedPrefixes = listOf("ai_api_key", "update_", "remote_", "backup_", "notification_permission_asked")

    fun isExcluded(key: String) = excludedPrefixes.any { key.startsWith(it) }

    fun filter(all: Map<String, *>): Map<String, Any> =
        all.entries
            .filter { (k, v) -> !isExcluded(k) && (v is String || v is Boolean || v is Int || v is Long || v is Float) }
            .associate { (k, v) -> k to v!! }

    fun encode(all: Map<String, *>): ByteArray {
        val arr = JSONArray()
        filter(all).forEach { (k, v) ->
            val type = when (v) {
                is Boolean -> "b"
                is Int -> "i"
                is Long -> "l"
                is Float -> "f"
                else -> "s"
            }
            arr.put(JSONObject().put("k", k).put("t", type).put("v", v))
        }
        return JSONObject().put("values", arr).toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): Map<String, Any> {
        val arr = JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("values")
        val out = LinkedHashMap<String, Any>()
        for (i in 0 until arr.length()) {
            val e = arr.getJSONObject(i)
            val k = e.getString("k")
            if (isExcluded(k)) continue
            out[k] = when (e.getString("t")) {
                "b" -> e.getBoolean("v")
                "i" -> e.getInt("v")
                "l" -> e.getLong("v")
                "f" -> e.getDouble("v").toFloat()
                else -> e.getString("v")
            }
        }
        return out
    }

    /** Replaces every restorable key with the backup's values; excluded keys stay as they are. */
    fun restore(prefs: SharedPreferences, values: Map<String, Any>) {
        val edit = prefs.edit()
        prefs.all.keys.filter { !isExcluded(it) }.forEach(edit::remove)
        values.forEach { (k, v) ->
            when (v) {
                is Boolean -> edit.putBoolean(k, v)
                is Int -> edit.putInt(k, v)
                is Long -> edit.putLong(k, v)
                is Float -> edit.putFloat(k, v)
                is String -> edit.putString(k, v)
            }
        }
        edit.commit()
    }
}
