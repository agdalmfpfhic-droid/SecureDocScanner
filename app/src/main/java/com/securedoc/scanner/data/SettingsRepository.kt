package com.securedoc.scanner.data

import android.content.SharedPreferences
import com.securedoc.scanner.data.model.ExportFormat
import com.securedoc.scanner.data.model.ImageQuality
import com.securedoc.scanner.data.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Réglages de l'app, stockés dans EncryptedSharedPreferences. */
class SettingsRepository(private val prefs: SharedPreferences) {

    private val _theme = MutableStateFlow(prefs.enumOr(KEY_THEME, ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()

    private val _exportFormat = MutableStateFlow(prefs.enumOr(KEY_FORMAT, ExportFormat.PDF))
    val exportFormat: StateFlow<ExportFormat> = _exportFormat.asStateFlow()

    private val _quality = MutableStateFlow(prefs.enumOr(KEY_QUALITY, ImageQuality.HIGH))
    val quality: StateFlow<ImageQuality> = _quality.asStateFlow()

    private val _lockEnabled = MutableStateFlow(prefs.getBoolean(KEY_LOCK, false))
    val lockEnabled: StateFlow<Boolean> = _lockEnabled.asStateFlow()

    private val _biometricEnabled = MutableStateFlow(prefs.getBoolean(KEY_BIOMETRIC, false))
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled.asStateFlow()

    fun setTheme(value: ThemeMode) { prefs.edit().putString(KEY_THEME, value.name).apply(); _theme.value = value }
    fun setExportFormat(value: ExportFormat) { prefs.edit().putString(KEY_FORMAT, value.name).apply(); _exportFormat.value = value }
    fun setQuality(value: ImageQuality) { prefs.edit().putString(KEY_QUALITY, value.name).apply(); _quality.value = value }
    fun setLockEnabled(value: Boolean) { prefs.edit().putBoolean(KEY_LOCK, value).apply(); _lockEnabled.value = value }
    fun setBiometricEnabled(value: Boolean) { prefs.edit().putBoolean(KEY_BIOMETRIC, value).apply(); _biometricEnabled.value = value }

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FORMAT = "export_format"
        const val KEY_QUALITY = "image_quality"
        const val KEY_LOCK = "lock_enabled"
        const val KEY_BIOMETRIC = "biometric_enabled"
    }
}

private inline fun <reified T : Enum<T>> SharedPreferences.enumOr(key: String, default: T): T =
    getString(key, null)?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default
