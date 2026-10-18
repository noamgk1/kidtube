package com.noam.kidtube.core

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256. There is deliberately no recovery path: any back door that lets a
 * parent back in lets the child in too. Losing the password means a destructive reset
 * plus a restore from an exported backup — which is why BackupCodec never carries the
 * password.
 */
object PasswordHasher {

    const val ITERATIONS: Int = 310_000
    private const val KEY_BITS = 256

    fun newSalt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    fun hash(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun install(settings: Settings, password: CharArray, iterations: Int = ITERATIONS): Settings {
        val salt = newSalt()
        val encoder = Base64.getEncoder()
        return settings.copy(
            passwordSaltBase64 = encoder.encodeToString(salt),
            passwordHashBase64 = encoder.encodeToString(hash(password, salt, iterations)),
        )
    }

    fun verify(settings: Settings, password: CharArray, iterations: Int = ITERATIONS): Boolean {
        val saltB64 = settings.passwordSaltBase64 ?: return false
        val hashB64 = settings.passwordHashBase64 ?: return false
        val decoder = Base64.getDecoder()
        val computed = hash(password, decoder.decode(saltB64), iterations)
        return MessageDigest.isEqual(computed, decoder.decode(hashB64))
    }

    fun clear(settings: Settings): Settings =
        settings.copy(passwordSaltBase64 = null, passwordHashBase64 = null)
}
