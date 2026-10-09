package com.serendeep.marginalia.ai

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Pkce {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
    private val random = SecureRandom()

    fun verifier(): String = randomString(64)

    fun challenge(verifier: String): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun randomString(length: Int): String =
        buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
}
