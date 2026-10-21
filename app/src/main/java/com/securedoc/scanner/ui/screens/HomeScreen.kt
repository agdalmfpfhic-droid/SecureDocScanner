@file:OptIn(ExperimentalMaterial3Api::class)

package com.securedoc.scanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securedoc.scanner.ui.HomeViewModel
import com.securedoc.scanner.ui.appViewModel
import com.securedoc.scanner.ui.components.DocumentRow
import com.securedoc.scanner.ui.components.EmptyState

@Composable
fun HomeScreen(
    onScan: () -> Unit,
    onImport: () -> Unit,
    onOpen: (Long) -> Unit,
    onSeeAll: () -> Unit,
) {
    val vm = appViewModel { HomeViewModel(it.repository) }
    val recent by vm.recent.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("SecureDoc Scanner") }) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Numérisez sans limite, sans publicité",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Button(onClick = onScan, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Icon(Icons.Filled.DocumentScanner, null)
                            Spacer(Modifier.size(10.dp))
                            Text("Scanner", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.PhotoLibrary, null)
                            Spacer(Modifier.size(8.dp))
                            Text("Importer depuis la galerie")
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(
                                Icons.Filled.CloudOff, null, Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                "Tout reste chiffré sur ce téléphone. Aucune connexion internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Documents récents", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (recent.isNotEmpty()) TextButton(onClick = onSeeAll) { Text("Tout voir") }
                }
            }
            if (recent.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.DocumentScanner,
                        "Aucun document pour l'instant",
                        "Touchez « Scanner » pour numériser votre premier document.",
                    )
                }
            }
            items(recent, key = { it.document.id }) { summary ->
                DocumentRow(summary, onClick = { onOpen(summary.document.id) })
            }
        }
    }
}
