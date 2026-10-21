package com.securedoc.scanner.security

import android.content.SharedPreferences
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class LockedOut(val seconds: Long) : PinResult
}

/**
 * Code PIN (4 à 8 chiffres). Seul un hachage PBKDF2-HMAC-SHA256 salé est conservé,
 * dans EncryptedSharedPreferences. Après 5 échecs, attente croissante (30 s, 60 s, 2 min…).
 */
class PinManager(private val prefs: SharedPreferences) {

    fun hasPin(): Boolean = prefs.contains(KEY_HASH)

    fun setPin(pin: String) {
        require(isValid(pin)) { "Le code doit contenir 4 à 8 chiffres" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, b64(salt))
            .putString(KEY_HASH, b64(hash(pin, salt)))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0)
            .apply()
    }

    fun clearPin() {
        prefs.edit().remove(KEY_HASH).remove(KEY_SALT).remove(KEY_FAILS).remove(KEY_LOCKED_UNTIL).apply()
    }

    fun verify(pin: String): PinResult {
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0)
        if (now < lockedUntil) return PinResult.LockedOut((lockedUntil - now) / 1000 + 1)

        val salt = prefs.getString(KEY_SALT, null)?.let(::unb64) ?: return PinResult.Wrong(0)
        val expected = prefs.getString(KEY_HASH, null)?.let(::unb64) ?: return PinResult.Wrong(0)

        if (MessageDigest.isEqual(hash(pin, salt), expected)) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0).apply()
            return PinResult.Success
        }

        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        return if (fails >= MAX_ATTEMPTS) {
            val waitMs = 30_000L shl (fails - MAX_ATTEMPTS).coerceAtMost(6)
            prefs.edit().putInt(KEY_FAILS, fails).putLong(KEY_LOCKED_UNTIL, now + waitMs).apply()
            PinResult.LockedOut(waitMs / 1000)
        } else {
            prefs.edit().putInt(KEY_FAILS, fails).apply()
            PinResult.Wrong(MAX_ATTEMPTS - fails)
        }
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun unb64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        fun isValid(pin: String) = pin.length in 4..8 && pin.all(Char::isDigit)

        private const val ITERATIONS = 120_000
        private const val MAX_ATTEMPTS = 5
        private const val KEY_HASH = "pin_hash"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_FAILS = "pin_fails"
        private const val KEY_LOCKED_UNTIL = "pin_locked_until"
    }
}
