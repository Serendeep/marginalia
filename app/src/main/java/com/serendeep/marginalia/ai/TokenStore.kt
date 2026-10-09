package com.serendeep.marginalia.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM with a non-exportable AndroidKeyStore key. Output is base64(iv || ciphertext). */
object SecretBox {
    private const val ALIAS = "marginalia_chatgpt"
    private const val PROVIDER = "AndroidKeyStore"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String? = try {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12)) }
        String(cipher.doFinal(bytes, 12, bytes.size - 12), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

data class ChatGptSession(
    val issuedClientId: String,
    val accountSub: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val scopes: Set<String>,
    val selectedModelSlug: String? = null,
)

class TokenStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("marginalia_chatgpt", Context.MODE_PRIVATE)

    @Synchronized
    fun extAgentHostId(): String =
        prefs.getString(HOST_ID, null) ?: "urn:uuid:${UUID.randomUUID()}".also { prefs.edit().putString(HOST_ID, it).apply() }

    @Synchronized
    fun session(): ChatGptSession? {
        val client = prefs.getString(CLIENT, null) ?: return null
        val access = prefs.getString(ACCESS, null)?.let(SecretBox::decrypt) ?: return null
        val refresh = prefs.getString(REFRESH, null)?.let(SecretBox::decrypt) ?: return null
        return ChatGptSession(
            issuedClientId = client,
            accountSub = prefs.getString(SUB, null) ?: return null,
            email = prefs.getString(EMAIL, "").orEmpty(),
            accessToken = access,
            refreshToken = refresh,
            expiresAt = prefs.getLong(EXPIRES_AT, 0),
            scopes = prefs.getString(SCOPES, "").orEmpty().split(' ').filter { it.isNotEmpty() }.toSet(),
            selectedModelSlug = prefs.getString(MODEL, null),
        )
    }

    @Synchronized
    fun save(s: ChatGptSession) {
        prefs.edit()
            .putString(CLIENT, s.issuedClientId)
            .putString(SUB, s.accountSub)
            .putString(EMAIL, s.email)
            .putString(ACCESS, SecretBox.encrypt(s.accessToken))
            .putString(REFRESH, SecretBox.encrypt(s.refreshToken))
            .putLong(EXPIRES_AT, s.expiresAt)
            .putString(SCOPES, s.scopes.joinToString(" "))
            .apply { if (s.selectedModelSlug != null) putString(MODEL, s.selectedModelSlug) else remove(MODEL) }
            .apply()
    }

    @Synchronized
    fun clear() {
        val host = prefs.getString(HOST_ID, null)
        prefs.edit().clear().apply { if (host != null) putString(HOST_ID, host) }.apply()
    }

    private companion object {
        const val HOST_ID = "ext_agent_host_id"
        const val CLIENT = "issued_client_id"
        const val SUB = "account_sub"
        const val EMAIL = "email"
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val EXPIRES_AT = "expires_at"
        const val SCOPES = "scopes"
        const val MODEL = "selected_model"
    }
}
