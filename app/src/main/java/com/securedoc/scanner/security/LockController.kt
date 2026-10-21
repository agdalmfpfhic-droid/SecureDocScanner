package com.securedoc.scanner.security

import android.os.SystemClock
import com.securedoc.scanner.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Verrouillage de l'application.
 * - Verrouillée au démarrage si le verrouillage est activé.
 * - Re-verrouillée après 15 s en arrière-plan.
 * - Délai étendu (10 min) quand l'app ouvre elle-même une autre activité
 *   (scanner ML Kit, sélecteur de fichiers, partage, code de l'appareil).
 */
class LockController(
    private val settings: SettingsRepository,
    private val pinManager: PinManager,
) {
    private val _locked = MutableStateFlow(isEnabled())
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var externalLaunchAt = 0L
    private var stoppedAt = 0L
    private var graceMs = BACKGROUND_GRACE_MS

    fun isEnabled(): Boolean = settings.lockEnabled.value && pinManager.hasPin()

    /** À appeler juste avant de lancer une activité externe. */
    fun expectExternal() {
        externalLaunchAt = SystemClock.elapsedRealtime()
    }

    fun onStop() {
        val now = SystemClock.elapsedRealtime()
        stoppedAt = now
        graceMs = if (now - externalLaunchAt < 5_000) EXTERNAL_GRACE_MS else BACKGROUND_GRACE_MS
    }

    fun onStart() {
        val away = SystemClock.elapsedRealtime() - stoppedAt
        if (stoppedAt > 0 && isEnabled() && away > graceMs) _locked.value = true
        stoppedAt = 0
    }

    fun lockNow() {
        if (isEnabled()) _locked.value = true
    }

    fun unlock() {
        _locked.value = false
    }

    private companion object {
        const val BACKGROUND_GRACE_MS = 15_000L
        const val EXTERNAL_GRACE_MS = 10 * 60_000L
    }
}
