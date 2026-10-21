package com.securedoc.scanner.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import java.util.UUID

/**
 * Stockage des images de pages, chiffrées avec EncryptedFile
 * (AES-256-GCM, segments de 4 Ko, clé Keystore). Rien n'est écrit en clair
 * sur le disque, sauf le PDF temporaire d'un partage demandé par l'utilisateur.
 */
class EncryptedFileStore(private val context: Context) {

    private val pagesDir = File(context.filesDir, "pages").apply { mkdirs() }
    val shareDir: File get() = File(context.cacheDir, "share").apply { mkdirs() }

    fun newPageFile(): File = File(pagesDir, "${UUID.randomUUID()}.enc")

    fun fileFor(name: String): File = File(pagesDir, name)

    fun write(file: File, bytes: ByteArray) {
        if (file.exists()) secureDelete(file) // EncryptedFile refuse d'écraser
        encrypted(file).openFileOutput().use { it.write(bytes) }
    }

    fun read(file: File): ByteArray = encrypted(file).openFileInput().use { it.readBytes() }

    /**
     * Suppression sécurisée : écrasement par des octets aléatoires puis suppression.
     * Sur mémoire flash, l'écrasement n'est pas garanti physiquement (wear leveling) ;
     * comme les fichiers sont déjà chiffrés, les données résiduelles restent illisibles.
     */
    fun secureDelete(file: File) {
        if (!file.exists()) return
        runCatching {
            RandomAccessFile(file, "rws").use { raf ->
                val random = SecureRandom()
                val buffer = ByteArray(8192)
                var remaining = raf.length()
                raf.seek(0)
                while (remaining > 0) {
                    random.nextBytes(buffer)
                    val n = minOf(remaining, buffer.size.toLong()).toInt()
                    raf.write(buffer, 0, n)
                    remaining -= n
                }
                raf.fd.sync()
            }
        }
        file.delete()
    }

    /** Efface les PDF temporaires de partage (appelé au démarrage et après chaque partage). */
    fun purgeShareCache() {
        shareDir.listFiles()?.forEach { secureDelete(it) }
    }

    /** Efface tous les fichiers de pages (y compris orphelins). */
    fun wipePages() {
        pagesDir.listFiles()?.forEach { secureDelete(it) }
    }

    private fun encrypted(file: File): EncryptedFile =
        EncryptedFile.Builder(
            context,
            file,
            Keys.masterKey(context),
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB,
        ).build()
}
