@file:OptIn(ExperimentalMaterial3Api::class)

package com.securedoc.scanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securedoc.scanner.security.BiometricHelper
import com.securedoc.scanner.ui.LocalAppContainer
import com.securedoc.scanner.ui.VaultViewModel
import com.securedoc.scanner.ui.appViewModel
import com.securedoc.scanner.ui.components.AuthGate
import com.securedoc.scanner.ui.components.DocumentRow
import com.securedoc.scanner.ui.components.EmptyState

/**
 * Coffre-fort : CIN, passeport, permis, carte bancaire…
 * Demande une authentification à chaque ouverture, se referme en quittant l'onglet.
 */
@Composable
fun VaultScreen(onOpen: (Long) -> Unit, onScan: () -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val vm = appViewModel { VaultViewModel(it.repository) }
    val unlocked by vm.unlocked.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()

    DisposableEffect(Unit) { onDispose { vm.lock() } }

    val canProtect = remember { container.pinManager.hasPin() || BiometricHelper.isAvailable(context) }

    if (!unlocked) {
        if (canProtect) {
            AuthGate(
                title = "Coffre-fort",
                subtitle = "Code PIN requis pour ouvrir le coffre",
                onUnlocked = vm::unlock,
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Shield, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text("Protégez votre coffre-fort", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Définissez un code PIN pour ranger vos pièces d'identité et cartes bancaires à l'abri.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onOpenSettings) { Text("Définir un code PIN") }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coffre-fort") },
                actions = {
                    IconButton(onClick = vm::lock) { Icon(Icons.Filled.Lock, "Refermer le coffre") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onScan,
                icon = { Icon(Icons.Filled.AddAPhoto, null) },
                text = { Text("Scanner une pièce") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 88.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.LockOpen, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(
                            "Coffre ouvert. Il se referme dès que vous quittez cet onglet ou l'application.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Shield,
                        "Coffre-fort vide",
                        "Les scans de CIN, passeport, permis et carte bancaire sont rangés ici automatiquement.",
                    )
                }
            }
            items(items, key = { it.document.id }) { s ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        s.document.profile.secureKind?.label ?: "Document sensible",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    DocumentRow(s, onClick = { onOpen(s.document.id) })
                }
            }
        }
    }
}
