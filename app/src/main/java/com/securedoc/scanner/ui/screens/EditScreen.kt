@file:OptIn(ExperimentalMaterial3Api::class)

package com.securedoc.scanner.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.securedoc.scanner.data.model.Document
import com.securedoc.scanner.data.model.ExportFormat
import com.securedoc.scanner.data.model.PageFilter
import com.securedoc.scanner.scan.rememberDocumentScanner
import com.securedoc.scanner.ui.EditEvent
import com.securedoc.scanner.ui.EditViewModel
import com.securedoc.scanner.ui.LocalAppContainer
import com.securedoc.scanner.ui.appViewModel
import com.securedoc.scanner.ui.components.PageImage
import com.securedoc.scanner.ui.formatDate

private enum class EditDialog { NONE, RENAME, DETAILS, MERGE, DELETE_DOC, DELETE_PAGE, SHARE }

@Composable
fun EditScreen(documentId: Long, onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val lock = container.lockController
    val vm = appViewModel(key = "edit-$documentId") { EditViewModel(it.repository, documentId) }
    val document by vm.document.collectAsStateWithLifecycle()
    val pages by vm.pages.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val defaultFormat by container.settings.exportFormat.collectAsStateWithLifecycle()

    var selected by rememberSaveable { mutableIntStateOf(0) }
    if (pages.isNotEmpty() && selected > pages.lastIndex) selected = pages.lastIndex
    val page = pages.getOrNull(selected)

    var dialog by remember { mutableStateOf(EditDialog.NONE) }
    var menuOpen by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var pendingImageFormat by remember { mutableStateOf(ExportFormat.JPEG) }
    val snackbar = remember { SnackbarHostState() }

    // --- Lanceurs : Storage Access Framework, partage, ajout de pages ---
    val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let(vm::exportPdf)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { vm.exportImages(it, pendingImageFormat) }
    }
    val shareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.clearShareCache()
    }
    val scanMore = rememberDocumentScanner(onResult = vm::addPages, onError = {})
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        if (uris.isNotEmpty()) vm.addPages(uris)
    }

    fun export(format: ExportFormat) {
        val name = document?.name ?: "document"
        lock.expectExternal()
        if (format == ExportFormat.PDF) createPdf.launch("$name.pdf")
        else { pendingImageFormat = format; pickFolder.launch(null) }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is EditEvent.Message -> snackbar.showSnackbar(event.text)
                is EditEvent.Closed -> onBack()
                is EditEvent.Share -> {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, event.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    lock.expectExternal()
                    shareLauncher.launch(Intent.createChooser(send, "Partager le PDF"))
                }
            }
        }
    }

    val doc = document
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        doc?.name ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { dialog = EditDialog.RENAME },
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { dialog = EditDialog.SHARE }, enabled = pages.isNotEmpty()) {
                        Icon(Icons.Filled.Share, "Partager")
                    }
                    Box {
                        IconButton(onClick = { exportMenuOpen = true }, enabled = pages.isNotEmpty()) {
                            Icon(Icons.Filled.FileDownload, "Exporter")
                        }
                        DropdownMenu(expanded = exportMenuOpen, onDismissRequest = { exportMenuOpen = false }) {
                            val ordered = listOf(defaultFormat) + ExportFormat.entries.filter { it != defaultFormat }
                            ordered.forEach { f ->
                                DropdownMenuItem(
                                    text = { Text(if (f == defaultFormat) "Exporter en ${f.label} (par défaut)" else "Exporter en ${f.label}") },
                                    onClick = { exportMenuOpen = false; export(f) },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Plus") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Renommer") }, onClick = { menuOpen = false; dialog = EditDialog.RENAME })
                            DropdownMenuItem(text = { Text("Dossier et coffre-fort") }, onClick = { menuOpen = false; dialog = EditDialog.DETAILS })
                            DropdownMenuItem(text = { Text("Fusionner avec un autre document") }, onClick = { menuOpen = false; dialog = EditDialog.MERGE })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Supprimer définitivement", color = MaterialTheme.colorScheme.error) },
                                onClick = { menuOpen = false; dialog = EditDialog.DELETE_DOC },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            // Grand aperçu de la page sélectionnée
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (page != null) PageImage(page, maxSide = 1600, modifier = Modifier.fillMaxSize().padding(8.dp))
                else Text("Aucune page", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (page != null) {
                // Outils de la page
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.rotate(page, -90) }) { Icon(Icons.AutoMirrored.Filled.RotateLeft, "Pivoter à gauche") }
                    IconButton(onClick = { vm.rotate(page, 90) }) { Icon(Icons.AutoMirrored.Filled.RotateRight, "Pivoter à droite") }
                    IconButton(
                        onClick = { vm.move(selected, selected - 1); selected -= 1 },
                        enabled = selected > 0,
                    ) { Icon(Icons.Filled.ChevronLeft, "Déplacer avant") }
                    Text("${selected + 1} / ${pages.size}", style = MaterialTheme.typography.labelLarge)
                    IconButton(
                        onClick = { vm.move(selected, selected + 1); selected += 1 },
                        enabled = selected < pages.lastIndex,
                    ) { Icon(Icons.Filled.ChevronRight, "Déplacer après") }
                    IconButton(onClick = { dialog = EditDialog.DELETE_PAGE }) {
                        Icon(Icons.Filled.DeleteForever, "Supprimer la page", tint = MaterialTheme.colorScheme.error)
                    }
                }

                // Filtres
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(PageFilter.entries) { f ->
                        FilterChip(selected = page.filter == f, onClick = { vm.setFilter(page, f) }, label = { Text(f.label) })
                    }
                    item {
                        TextButton(onClick = { vm.applyFilterToAll(page.filter) }) { Text("Appliquer à toutes") }
                    }
                }
            }

            // Bande des pages
            Surface(tonalElevation = 2.dp) {
                LazyRow(
                    modifier = Modifier.navigationBarsPadding(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { index, p ->
                        val isSelected = index == selected
                        Box(
                            Modifier
                                .size(width = 60.dp, height = 80.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    ),
                                    RoundedCornerShape(6.dp),
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selected = index },
                        ) {
                            PageImage(p, maxSide = 240, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(topStart = 4.dp))
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                    item {
                        Box {
                            Box(
                                Modifier
                                    .size(width = 60.dp, height = 80.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                    .clickable { addMenuOpen = true },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Filled.Add, "Ajouter des pages") }
                            DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Scanner des pages") },
                                    onClick = { addMenuOpen = false; doc?.let { scanMore(it.profile) } },
                                )
                                DropdownMenuItem(
                                    text = { Text("Depuis la galerie") },
                                    onClick = {
                                        addMenuOpen = false
                                        lock.expectExternal()
                                        pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Dialogues ---
    when (dialog) {
        EditDialog.RENAME -> RenameDialog(doc?.name.orEmpty(), onDismiss = { dialog = EditDialog.NONE }) {
            vm.rename(it); dialog = EditDialog.NONE
        }
        EditDialog.DETAILS -> if (doc != null) AlertDialog(
            onDismissRequest = { dialog = EditDialog.NONE },
            confirmButton = { TextButton(onClick = { dialog = EditDialog.NONE }) { Text("Fermer") } },
            title = { Text("Dossier et coffre-fort") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CategoryDropdown(categories, doc.categoryId, null, vm::setCategory)
                    ListItem(
                        headlineContent = { Text("Ranger dans le coffre-fort") },
                        supportingContent = { Text("Masqué de la bibliothèque, accès protégé par PIN ou biométrie") },
                        trailingContent = { Switch(checked = doc.inVault, onCheckedChange = vm::setVault) },
                    )
                    Text(
                        "Type : ${doc.profile.label} · créé le ${formatDate(doc.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
        )
        EditDialog.MERGE -> MergeDialog(
            load = vm::mergeCandidates,
            onDismiss = { dialog = EditDialog.NONE },
            onPick = { vm.merge(it.id); dialog = EditDialog.NONE },
        )
        EditDialog.DELETE_DOC -> ConfirmDialog(
            title = "Supprimer définitivement ?",
            body = "Toutes les pages sont écrasées puis effacées de l'appareil. Cette action est irréversible.",
            confirm = "Supprimer",
            onDismiss = { dialog = EditDialog.NONE },
            onConfirm = { vm.deleteDocument(); dialog = EditDialog.NONE },
        )
        EditDialog.DELETE_PAGE -> ConfirmDialog(
            title = "Supprimer la page ${selected + 1} ?",
            body = "La page est effacée définitivement de l'appareil.",
            confirm = "Supprimer",
            onDismiss = { dialog = EditDialog.NONE },
            onConfirm = { page?.let(vm::deletePage); dialog = EditDialog.NONE },
        )
        EditDialog.SHARE -> ConfirmDialog(
            title = "Partager ce document ?",
            body = "Un PDF non chiffré va être transmis à l'application que vous choisirez. " +
                "Une fois envoyé, SecureDoc ne contrôle plus ce que cette application en fait.",
            confirm = "Continuer",
            onDismiss = { dialog = EditDialog.NONE },
            onConfirm = { vm.share(); dialog = EditDialog.NONE },
        )
        EditDialog.NONE -> Unit
    }
}

@Composable
private fun RenameDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renommer") },
        text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true, label = { Text("Nom") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun MergeDialog(load: suspend () -> List<Document>, onDismiss: () -> Unit, onPick: (Document) -> Unit) {
    val candidates by produceState<List<Document>?>(null) { value = load() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fusionner avec…") },
        text = {
            val list = candidates
            when {
                list == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                list.isEmpty() -> Text("Aucun autre document.")
                else -> LazyColumn {
                    items(list, key = { it.id }) { d ->
                        ListItem(
                            headlineContent = { Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("Ses pages seront ajoutées à la fin. ${d.profile.label}") },
                            modifier = Modifier.clickable { onPick(d) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
