package com.securedoc.scanner.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom

/** Clé maîtresse AES-256-GCM générée et conservée dans Android Keystore (non exportable). */
object Keys {
    @Volatile private var masterKey: MasterKey? = null

    fun masterKey(context: Context): MasterKey =
        masterKey ?: synchronized(this) {
            masterKey ?: MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
                .also { masterKey = it }
        }
}

/**
 * Préférences et métadonnées sensibles : clés et valeurs chiffrées
 * (AES-256-SIV / AES-256-GCM) avec la clé Keystore.
 */
class SecurePrefs(context: Context) {

    val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "securedoc_secure_prefs",
        Keys.masterKey(context),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    /** Phrase de passe SQLCipher : 256 bits aléatoires, créés au premier lancement. */
    fun databasePassphrase(): ByteArray {
        prefs.getString(KEY_DB_PASSPHRASE, null)?.let {
            return Base64.decode(it, Base64.NO_WRAP)
        }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_DB_PASSPHRASE, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .commit()
        return bytes
    }

    private companion object {
        const val KEY_DB_PASSPHRASE = "db_passphrase"
    }
}
