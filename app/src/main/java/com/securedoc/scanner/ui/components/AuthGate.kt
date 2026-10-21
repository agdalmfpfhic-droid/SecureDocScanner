package com.securedoc.scanner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securedoc.scanner.security.BiometricHelper
import com.securedoc.scanner.security.PinResult
import com.securedoc.scanner.ui.LocalAppContainer
import com.securedoc.scanner.ui.findActivity

/**
 * Écran d'authentification (application ou coffre-fort) :
 * code PIN, et biométrie / code de l'appareil si activé.
 */
@Composable
fun AuthGate(
    title: String,
    subtitle: String,
    onUnlocked: () -> Unit,
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val biometricSetting by container.settings.biometricEnabled.collectAsStateWithLifecycle()
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }
    val hasPin = remember { container.pinManager.hasPin() }
    val useBiometric = biometricAvailable && (biometricSetting || !hasPin)
    var error by remember { mutableStateOf<String?>(null) }

    val launchBiometric = {
        container.lockController.expectExternal()
        BiometricHelper.authenticate(activity, title, onSuccess = onUnlocked, onError = { error = it })
    }

    LaunchedEffect(Unit) { if (useBiometric) launchBiometric() }

    if (!hasPin) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { launchBiometric() }, enabled = useBiometric) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.size(8.dp))
                Text("Déverrouiller")
            }
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
        return
    }

    PinPad(
        title = title,
        subtitle = subtitle,
        error = error,
        onSubmit = { pin ->
            when (val r = container.pinManager.verify(pin)) {
                PinResult.Success -> onUnlocked()
                is PinResult.Wrong -> error =
                    if (r.attemptsLeft > 0) "Code incorrect. ${r.attemptsLeft} essai(s) avant attente." else "Code incorrect."
                is PinResult.LockedOut -> error = "Trop d'essais. Réessayez dans ${r.seconds} s."
            }
        },
        onBiometric = if (useBiometric) launchBiometric else null,
    )
}

@Composable
fun PinPad(
    title: String,
    subtitle: String,
    error: String?,
    onSubmit: (String) -> Unit,
    onBiometric: (() -> Unit)?,
) {
    var pin by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.semantics { contentDescription = "${pin.length} chiffres saisis" },
        ) {
            repeat(maxOf(4, pin.length)) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            error ?: " ",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { d -> PadKey(onClick = { if (pin.length < 8) pin += d }) { Text(d, style = MaterialTheme.typography.headlineSmall) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                if (onBiometric != null) {
                    PadKey(onClick = onBiometric) { Icon(Icons.Filled.Fingerprint, "Biométrie") }
                } else {
                    Spacer(Modifier.size(72.dp))
                }
                PadKey(onClick = { if (pin.length < 8) pin += "0" }) { Text("0", style = MaterialTheme.typography.headlineSmall) }
                PadKey(onClick = { pin = pin.dropLast(1) }) { Icon(Icons.AutoMirrored.Filled.Backspace, "Effacer") }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(pin); pin = "" },
            enabled = pin.length >= 4,
            modifier = Modifier.fillMaxWidth(0.7f),
        ) { Text("Valider") }
    }
}

@Composable
private fun PadKey(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(72.dp).clip(CircleShape).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
