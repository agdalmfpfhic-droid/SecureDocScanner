package com.securedoc.scanner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.securedoc.scanner.data.model.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5F8B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFCDE5FF),
    onPrimaryContainer = Color(0xFF001D32),
    secondary = Color(0xFF4F6070),
    secondaryContainer = Color(0xFFD3E4F7),
    tertiary = Color(0xFF2E7D5B),        // vert « en sécurité »
    tertiaryContainer = Color(0xFFB3F1D2),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFF7F9FC),
    surfaceVariant = Color(0xFFDEE3EB),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CCAFF),
    onPrimary = Color(0xFF003352),
    primaryContainer = Color(0xFF004B74),
    onPrimaryContainer = Color(0xFFCDE5FF),
    secondary = Color(0xFFB7C8DA),
    secondaryContainer = Color(0xFF384956),
    tertiary = Color(0xFF8BD5B4),
    tertiaryContainer = Color(0xFF0F5139),
    background = Color(0xFF101418),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF41474D),
    error = Color(0xFFF2B8B5),
)

@Composable
fun SecureDocTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
