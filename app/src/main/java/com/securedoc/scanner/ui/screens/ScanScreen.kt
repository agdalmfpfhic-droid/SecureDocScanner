@file:OptIn(ExperimentalMaterial3Api::class)

package com.securedoc.scanner.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securedoc.scanner.data.model.Category
import com.securedoc.scanner.data.model.ScanProfile
import com.securedoc.scanner.scan.rememberDocumentScanner
import com.securedoc.scanner.ui.LocalAppContainer
import com.securedoc.scanner.ui.ScanViewModel
import com.securedoc.scanner.ui.appViewModel
import com.securedoc.scanner.ui.components.UriImage

fun profileIcon(p: ScanProfile): ImageVector = when (p) {
    ScanProfile.A4, ScanProfile.A5 -> Icons.Filled.Description
    ScanProfile.RECEIPT -> Icons.Filled.Receipt
    ScanProfile.PASSPORT -> Icons.Filled.Public
    ScanProfile.NATIONAL_ID -> Icons.Filled.Badge
    ScanProfile.DRIVING_LICENSE -> Icons.Filled.DirectionsCar
    ScanProfile.BANK_CARD -> Icons.Filled.CreditCard
    ScanProfile.CONTRACT -> Icons.Filled.Gavel
    ScanProfile.HANDWRITTEN -> Icons.Filled.StickyNote2
    ScanProfile.BOOK -> Icons.Filled.MenuBook
}

@Composable
fun ScanScreen(
    importMode: Boolean,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
) {
    val vm = appViewModel { ScanViewModel(it.repository) }
    val state by vm.state.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val lock = LocalAppContainer.current.lockController
    val snackbar = remember { SnackbarHostState() }

    val startScan = rememberDocumentScanner(onResult = vm::addPending, onError = vm::showError)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.addPending(uris)
    }
    val launchPicker = {
        lock.expectExternal()
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); vm.clearError() }
    }

    val previewing = state.pending.isNotEmpty()
    BackHandler(enabled = previewing) { vm.cancel() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            previewing -> "Aperçu · ${state.pending.size} page(s)"
                            importMode -> "Importer des photos"
                            else -> "Nouveau scan"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (previewing) vm.cancel() else onBack() }) {
                        Icon(
                            if (previewing) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            if (previewing) "Annuler" else "Retour",
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!previewing) {
            ProfilePicker(
                padding = padding,
                selected = state.profile,
                name = state.name,
                categoryId = state.categoryId,
                categories = categories,
                importMode = importMode,
                onSelect = vm::selectProfile,
                onName = vm::setName,
                onCategory = vm::setCategory,
                onStart = { if (importMode) launchPicker() else startScan(state.profile) },
            )
        } else {
            PreviewPane(
                padding = padding,
                pending = state.pending,
                saving = state.saving,
                onRemove = vm::removePending,
                onAddScan = { startScan(state.profile) },
                onAddPhotos = launchPicker,
                onSave = { vm.save(onSaved) },
            )
        }
    }
}

@Composable
private fun ProfilePicker(
    padding: PaddingValues,
    selected: ScanProfile,
    name: String,
    categoryId: Long?,
    categories: List<Category>,
    importMode: Boolean,
    onSelect: (ScanProfile) -> Unit,
    onName: (String) -> Unit,
    onCategory: (Long?) -> Unit,
    onStart: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(152.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Type de document", style = MaterialTheme.typography.titleMedium)
            }
            items(ScanProfile.entries) { profile ->
                val isSelected = profile == selected
                Card(
                    onClick = { onSelect(profile) },
                    border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(profileIcon(profile), null, tint = MaterialTheme.colorScheme.primary)
                        Text(profile.label, style = MaterialTheme.typography.titleSmall, minLines = 2, maxLines = 2)
                        Text(profile.formatText, style = MaterialTheme.typography.bodySmall)
                        Text(
                            if (profile.secureKind != null) "Coffre-fort · ${profile.pagesText}" else profile.pagesText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = onName,
                        label = { Text("Nom (facultatif)") },
                        placeholder = { Text("${selected.label} + date") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CategoryDropdown(
                        categories = categories,
                        selectedId = categoryId,
                        fallbackName = selected.defaultCategory,
                        onSelect = onCategory,
                    )
                    Text(
                        "Le scanner détecte les bords, corrige la perspective et l'orientation, " +
                            "et peut retirer ombres et taches. Le recadrage se règle dans son éditeur.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
            ) {
                Icon(if (importMode) Icons.Filled.PhotoLibrary else Icons.Filled.DocumentScanner, null)
                Spacer(Modifier.size(8.dp))
                Text(if (importMode) "Choisir les photos" else "Scanner : ${selected.label}")
            }
        }
    }
}

@Composable
fun CategoryDropdown(
    categories: List<Category>,
    selectedId: Long?,
    fallbackName: String?,
    onSelect: (Long?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = categories.firstOrNull { it.id == selectedId }?.name
        ?: fallbackName?.let { "$it (par défaut)" }
        ?: "Sans catégorie"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = current,
            onValueChange = {},
            readOnly = true,
            label = { Text("Dossier") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onSelect(c.id); expanded = false })
            }
        }
    }
}

@Composable
private fun PreviewPane(
    padding: PaddingValues,
    pending: List<Uri>,
    saving: Boolean,
    onRemove: (Uri) -> Unit,
    onAddScan: () -> Unit,
    onAddPhotos: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(pending, key = { _, uri -> uri.toString() }) { index, uri ->
                Box {
                    UriImage(
                        uri, maxSide = 500,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.72f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    FilledIconButton(
                        onClick = { onRemove(uri) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) { Icon(Icons.Filled.Close, "Retirer la page", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface) }
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onAddScan, modifier = Modifier.weight(1f), enabled = !saving) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.size(4.dp)); Text("Scanner")
                    }
                    OutlinedButton(onClick = onAddPhotos, modifier = Modifier.weight(1f), enabled = !saving) {
                        Icon(Icons.Filled.Add, null); Spacer(Modifier.size(4.dp)); Text("Photos")
                    }
                }
                Button(onClick = onSave, enabled = !saving, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Enregistrer et chiffrer")
                }
            }
        }
    }
}
