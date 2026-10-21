@file:OptIn(ExperimentalMaterial3Api::class)

package com.securedoc.scanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securedoc.scanner.data.model.ScanProfile
import com.securedoc.scanner.ui.LibraryViewModel
import com.securedoc.scanner.ui.Period
import com.securedoc.scanner.ui.appViewModel
import com.securedoc.scanner.ui.components.DocumentRow
import com.securedoc.scanner.ui.components.EmptyState
import com.securedoc.scanner.ui.components.categoryIcon

@Composable
fun LibraryScreen(onOpen: (Long) -> Unit) {
    val vm = appViewModel { LibraryViewModel(it.repository) }
    val query by vm.query.collectAsStateWithLifecycle()
    val categoryId by vm.categoryId.collectAsStateWithLifecycle()
    val profile by vm.profile.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Bibliothèque") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                placeholder = { Text("Rechercher par nom, type ou date") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, "Effacer") }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = categoryId == null, onClick = { vm.categoryId.value = null }, label = { Text("Tous") })
                }
                items(categories, key = { it.id }) { c ->
                    FilterChip(
                        selected = categoryId == c.id,
                        onClick = { vm.categoryId.value = if (categoryId == c.id) null else c.id },
                        label = { Text(c.name) },
                        leadingIcon = { Icon(categoryIcon(c.name), null, Modifier.size(18.dp)) },
                    )
                }
            }

            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuChip(
                    label = profile?.label ?: "Tous les types",
                    options = listOf<ScanProfile?>(null) + ScanProfile.entries,
                    optionLabel = { it?.label ?: "Tous les types" },
                    onSelect = { vm.profile.value = it },
                )
                MenuChip(
                    label = period.label,
                    options = Period.entries,
                    optionLabel = { it.label },
                    onSelect = { vm.period.value = it },
                )
            }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                item {
                    Text(
                        if (results.size == 1) "1 document" else "${results.size} documents",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (results.isEmpty()) {
                    item {
                        EmptyState(
                            Icons.Filled.SearchOff,
                            "Aucun document trouvé",
                            "Modifiez la recherche ou les filtres. Les documents du coffre-fort n'apparaissent pas ici.",
                        )
                    }
                }
                items(results, key = { it.document.id }) { s ->
                    DocumentRow(s, onClick = { onOpen(s.document.id) })
                }
            }
        }
    }
}

@Composable
private fun <T> MenuChip(
    label: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(
            onClick = { open = true },
            label = { Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onSelect(o); open = false })
            }
        }
    }
}
