package com.serendeep.marginalia.update

import android.content.Context
import android.content.SharedPreferences
import com.serendeep.marginalia.shell.PREFS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class RemoteMessage(val text: String, val url: String?)

data class RemoteFlags(
    val ai: Boolean = true,
    val agent: Boolean = true,
    val autoSort: Boolean = true,
    val handwritingSearch: Boolean = true,
)

/** Server-controlled switches. Every field is optional; what is missing keeps its safe default. */
data class RemoteConfig(
    val minSupportedVersionCode: Long = 0,
    val knownBadVersionCodes: Set<Long> = emptySet(),
    val message: RemoteMessage? = null,
    val flags: RemoteFlags = RemoteFlags(),
) {
    fun mustUpdate(installed: Long): Boolean = installed < minSupportedVersionCode || installed in knownBadVersionCodes

    /** The agent flag has no surface of its own: Ask and the notebook panel are the only agent UI, so it gates all AI. */
    val aiAllowed: Boolean get() = flags.ai && flags.agent

    companion object {
        const val CACHE_KEY = "remote_config_json"

        fun parse(json: String): RemoteConfig? = try {
            val o = JSONObject(json)
            val bad = o.optJSONArray("knownBadVersionCodes")
            val msg = o.optJSONObject("message")
            val f = o.optJSONObject("flags")
            val defaults = RemoteFlags()
            RemoteConfig(
                minSupportedVersionCode = o.optLong("minSupportedVersionCode", 0),
                knownBadVersionCodes = bad?.let { a -> (0 until a.length()).map { a.optLong(it, -1) }.filter { it > 0 }.toSet() }.orEmpty(),
                message = msg?.optString("text")?.takeIf { it.isNotBlank() }?.let { text ->
                    RemoteMessage(text, msg.optString("url").takeIf { it.isNotEmpty() && UpdateInfo.isSafeUrl(it) })
                },
                flags = RemoteFlags(
                    ai = f?.optBoolean("ai", defaults.ai) ?: defaults.ai,
                    agent = f?.optBoolean("agent", defaults.agent) ?: defaults.agent,
                    autoSort = f?.optBoolean("autoSort", defaults.autoSort) ?: defaults.autoSort,
                    handwritingSearch = f?.optBoolean("handwritingSearch", defaults.handwritingSearch) ?: defaults.handwritingSearch,
                ),
            )
        } catch (_: Exception) {
            null
        }

        /** The last config that parsed, or the defaults. */
        fun cached(prefs: SharedPreferences): RemoteConfig =
            prefs.getString(CACHE_KEY, null)?.let(::parse) ?: RemoteConfig()
    }
}

private const val DISMISSED_KEY = "remote_message_dismissed"

@Singleton
class RemoteConfigStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _config = MutableStateFlow(RemoteConfig.cached(prefs))
    val config: StateFlow<RemoteConfig> = _config.asStateFlow()

    private val _dismissed = MutableStateFlow(prefs.getString(DISMISSED_KEY, null))

    /** Text of the info message the user closed, so the same message stays away. */
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    fun dismiss(text: String) {
        prefs.edit().putString(DISMISSED_KEY, text).apply()
        _dismissed.value = text
    }

    /** Keeps [json] as the new cache only if it parses; a bad response leaves the old config in place. */
    fun update(json: String): Boolean {
        val parsed = RemoteConfig.parse(json) ?: return false
        prefs.edit().putString(RemoteConfig.CACHE_KEY, json).apply()
        _config.value = parsed
        return true
    }
}
