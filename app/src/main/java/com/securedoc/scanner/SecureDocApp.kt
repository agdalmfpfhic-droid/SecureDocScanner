package com.securedoc.scanner

import android.app.Application
import android.content.Context
import com.securedoc.scanner.data.DocumentRepository
import com.securedoc.scanner.data.SettingsRepository
import com.securedoc.scanner.data.db.AppDatabase
import com.securedoc.scanner.security.EncryptedFileStore
import com.securedoc.scanner.security.LockController
import com.securedoc.scanner.security.PinManager
import com.securedoc.scanner.security.SecurePrefs

class SecureDocApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Un partage interrompu peut laisser un PDF temporaire : on l'efface.
        container.fileStore.purgeShareCache()
    }
}

/** Injection de dépendances manuelle (pas de framework, APK plus léger). */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs = SecurePrefs(appContext)

    val settings = SettingsRepository(securePrefs.prefs)
    val pinManager = PinManager(securePrefs.prefs)
    val lockController = LockController(settings, pinManager)
    val fileStore = EncryptedFileStore(appContext)

    private val database: AppDatabase by lazy {
        AppDatabase.build(appContext, securePrefs.databasePassphrase())
    }

    val repository: DocumentRepository by lazy {
        DocumentRepository(appContext, database, fileStore, settings)
    }
}
