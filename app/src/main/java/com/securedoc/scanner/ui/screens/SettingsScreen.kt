@file:OptIn(ExperimentalMaterial3Api::class)

package com.securedoc.scanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securedoc.scanner.data.model.ExportFormat
import com.securedoc.scanner.data.model.ImageQuality
import com.securedoc.scanner.data.model.ThemeMode
import com.securedoc.scanner.security.BiometricHelper
import com.securedoc.scanner.security.PinManager
import com.securedoc.scanner.ui.SettingsViewModel
import com.securedoc.scanner.ui.appViewModel

const val PRIVACY_POLICY = """Vos documents restent sur votre téléphone.

• SecureDoc Scanner n'a pas accès à internet : l'application ne déclare aucune permission réseau. Aucune image, aucun PDF, aucun texte et aucune métadonnée n'est envoyé à un serveur.
• Le scan est réalisé par ML Kit Document Scanner, dans les services Google Play de l'appareil. Le traitement des images se fait sur le téléphone.
• Les pages sont chiffrées (AES-256) et la base de données est chiffrée par SQLCipher. Les clés sont protégées par Android Keystore.
• Pas de compte, pas de publicité, pas de statistiques d'usage, pas de télémétrie.
• Aucune sauvegarde cloud automatique : les données sont exclues des sauvegardes Android.
• Un document ne quitte l'appareil que si vous l'exportez vers un dossier de votre choix ou le partagez vous-même, après confirmation.
• Les métadonnées EXIF (position GPS, modèle d'appareil) sont supprimées des images enregistrées.
• « Effacer tous les documents » ou la désinstallation de l'application suppriment toutes les données."""

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val vm = appViewModel { SettingsViewModel(it.settings, it.pinManager, it.lockController, it.repository) }
    val lockEnabled by vm.settings.lockEnabled.collectAsStateWithLifecycle()
    val biometric by vm.settings.biometricEnabled.collectAsStateWithLifecycle()
    val theme by vm.settings.theme.collectAsStateWithLifecycle()
    val format by vm.settings.exportFormat.collectAsStateWithLifecycle()
    val quality by vm.settings.quality.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val biometricAvailable = remember { BiometricHelper.isAvailable(context) }

    var showPinDialog by remember { mutableStateOf(false) }
    var showWipeDialog by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Paramètres") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        ) {
            Section("Sécurité")
            ListItem(
                headlineContent = { Text("Verrouiller l'application") },
                supportingContent = { Text("Code PIN demandé à l'ouverture et après 15 s en arrière-plan") },
                trailingContent = {
                    Switch(
                        checked = lockEnabled && hasPin,
                        onCheckedChange = { on -> if (on) showPinDialog = true else vm.disableLock() },
                    )
                },
            )
            ListItem(
                headlineContent = { Text(if (hasPin) "Modifier le code PIN" else "Définir un code PIN") },
                supportingContent = { Text("4 à 8 chiffres. Protège aussi le coffre-fort.") },
                modifier = Modifier.clickable { showPinDialog = true },
            )
            ListItem(
                headlineContent = { Text("Biométrie ou verrouillage de l'appareil") },
                supportingContent = {
                    Text(
                        if (biometricAvailable) "Empreinte, visage, ou le code / schéma de votre téléphone"
                        else "Aucune empreinte ni verrouillage d'écran configuré sur ce téléphone"
                    )
                },
                trailingContent = {
                    Switch(checked = biometric && biometricAvailable, enabled = biometricAvailable, onCheckedChange = vm::setBiometric)
                },
            )
            ListItem(
                headlineContent = { Text("Captures d'écran bloquées") },
                supportingContent = { Text("Toujours actif. Le contenu est aussi masqué dans les applications récentes.") },
            )
            if (lockEnabled && hasPin) {
                ListItem(
                    headlineContent = { Text("Verrouiller maintenant") },
                    modifier = Modifier.clickable { vm.lockNow() },
                )
            }
            if (hasPin) {
                ListItem(
                    headlineContent = { Text("Supprimer le code PIN") },
                    supportingContent = { Text("Désactive aussi le verrouillage") },
                    modifier = Modifier.clickable { vm.removePin() },
                )
            }
            ListItem(
                headlineContent = { Text("Effacer tous les documents", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Suppression sécurisée et définitive de toutes les pages") },
                modifier = Modifier.clickable { showWipeDialog = true },
            )

            Section("Documents")
            Labeled("Format d'export par défaut") {
                Segmented(ExportFormat.entries, format, { it.label }, vm::setFormat)
            }
            Labeled("Qualité d'image") {
                Segmented(ImageQuality.entries, quality, { it.label }, vm::setQuality)
            }

            Section("Apparence")
            Labeled("Thème") {
                Segmented(ThemeMode.entries, theme, { it.label }, vm::setTheme)
            }
            ListItem(
                headlineContent = { Text("Langue") },
                supportingContent = { Text("Français (seule langue de cette version)") },
            )

            Section("Confidentialité")
            ListItem(
                headlineContent = { Text("Politique de confidentialité") },
                supportingContent = { Text("Vos documents restent sur l'appareil") },
                modifier = Modifier.clickable { showPrivacy = true },
            )
            ListItem(
                headlineContent = { Text("SecureDoc Scanner 1.0.0") },
                supportingContent = { Text("Sans publicité · sans compte · hors ligne") },
            )
        }
    }

    if (showPinDialog) {
        SetPinDialog(onDismiss = { showPinDialog = false }) { pin ->
            vm.setPin(pin)
            showPinDialog = false
        }
    }
    if (showWipeDialog) {
        ConfirmDialog(
            title = "Effacer tous les documents ?",
            body = "Toutes les pages, y compris celles du coffre-fort, sont écrasées puis supprimées. Cette action est irréversible.",
            confirm = "Tout effacer",
            onDismiss = { showWipeDialog = false },
            onConfirm = { vm.wipeAll(); showWipeDialog = false },
        )
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Confidentialité") },
            text = { Text(PRIVACY_POLICY, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Fermer") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(
                selected = o == selected,
                onClick = { onSelect(o) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label(o)) }
        }
    }
}

@Composable
private fun SetPinDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = PinManager.isValid(pin)
    val matches = pin == confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Code PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) pin = it },
                    label = { Text("Nouveau code (4 à 8 chiffres)") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { if (it.length <= 8 && it.all(Char::isDigit)) confirm = it },
                    label = { Text("Confirmer le code") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    isError = confirm.isNotEmpty() && !matches,
                    supportingText = { if (confirm.isNotEmpty() && !matches) Text("Les deux codes sont différents") },
                )
                Card {
                    Text(
                        "Notez-le : sans ce code ni biométrie, les documents ne sont plus accessibles.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(pin) }, enabled = valid && matches) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
