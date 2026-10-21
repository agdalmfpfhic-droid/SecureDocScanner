package com.securedoc.scanner.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.securedoc.scanner.data.DocumentRepository
import com.securedoc.scanner.data.SettingsRepository
import com.securedoc.scanner.data.model.Category
import com.securedoc.scanner.data.model.Document
import com.securedoc.scanner.data.model.DocumentSummary
import com.securedoc.scanner.data.model.ExportFormat
import com.securedoc.scanner.data.model.ImageQuality
import com.securedoc.scanner.data.model.Page
import com.securedoc.scanner.data.model.PageFilter
import com.securedoc.scanner.data.model.ScanProfile
import com.securedoc.scanner.data.model.ThemeMode
import com.securedoc.scanner.security.LockController
import com.securedoc.scanner.security.PinManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

private fun <T> Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

// ---------------- Accueil ----------------

class HomeViewModel(repo: DocumentRepository) : ViewModel() {
    val recent: StateFlow<List<DocumentSummary>> = repo.summaries(vault = false).map { it.take(8) }.stateIn(this, emptyList())
}

// ---------------- Scan ----------------

data class ScanUiState(
    val profile: ScanProfile = ScanProfile.A4,
    val name: String = "",
    val categoryId: Long? = null,
    val pending: List<Uri> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
)

class ScanViewModel(private val repo: DocumentRepository) : ViewModel() {
    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(this, emptyList())

    fun selectProfile(profile: ScanProfile) = _state.update { it.copy(profile = profile, categoryId = null) }
    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setCategory(id: Long?) = _state.update { it.copy(categoryId = id) }
    fun addPending(uris: List<Uri>) = _state.update { it.copy(pending = it.pending + uris, error = null) }
    fun removePending(uri: Uri) {
        _state.update { it.copy(pending = it.pending - uri) }
        repo.discardTemporary(listOf(uri))
    }
    fun showError(message: String) = _state.update { it.copy(error = message) }
    fun clearError() = _state.update { it.copy(error = null) }

    /** Annule l'aperçu : les images temporaires sont effacées, rien n'est enregistré. */
    fun cancel() {
        val uris = _state.value.pending
        _state.update { it.copy(pending = emptyList()) }
        repo.discardTemporary(uris)
    }

    fun save(onSaved: (Long) -> Unit) {
        val s = _state.value
        if (s.pending.isEmpty() || s.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repo.createDocument(s.pending, s.profile, s.name, s.categoryId) }
                .onSuccess { id ->
                    _state.update { it.copy(pending = emptyList(), saving = false) }
                    onSaved(id)
                }
                .onFailure { e -> _state.update { it.copy(saving = false, error = "Enregistrement impossible : ${e.message}") } }
        }
    }

    override fun onCleared() {
        repo.discardTemporary(_state.value.pending)
    }
}

// ---------------- Édition ----------------

sealed interface EditEvent {
    data class Message(val text: String) : EditEvent
    data class Share(val uri: Uri) : EditEvent
    data object Closed : EditEvent
}

class EditViewModel(private val repo: DocumentRepository, private val documentId: Long) : ViewModel() {
    val document: StateFlow<Document?> = repo.document(documentId).stateIn(this, null)
    val pages: StateFlow<List<Page>> = repo.pages(documentId).stateIn(this, emptyList())
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(this, emptyList())

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<EditEvent>(Channel.BUFFERED)
    val events: Flow<EditEvent> = _events.receiveAsFlow()

    private fun op(success: String? = null, showBusy: Boolean = false, block: suspend () -> Unit) {
        viewModelScope.launch {
            if (showBusy) _busy.value = true
            runCatching { block() }
                .onSuccess { success?.let { _events.send(EditEvent.Message(it)) } }
                .onFailure { _events.send(EditEvent.Message("Erreur : ${it.message ?: "inconnue"}")) }
            if (showBusy) _busy.value = false
        }
    }

    fun rotate(page: Page, delta: Int) = op { repo.rotate(page, delta) }
    fun setFilter(page: Page, filter: PageFilter) = op { repo.setFilter(page, filter) }
    fun applyFilterToAll(filter: PageFilter) = op("Filtre « ${filter.label} » appliqué à toutes les pages") {
        repo.setFilterForAll(documentId, filter)
    }
    fun move(from: Int, to: Int) = op { repo.movePage(documentId, from, to) }
    fun deletePage(page: Page) = op("Page supprimée définitivement") { repo.deletePage(page) }
    fun addPages(uris: List<Uri>) = op("${uris.size} page(s) ajoutée(s)", showBusy = true) { repo.addPages(documentId, uris) }
    fun rename(name: String) = op("Document renommé") { repo.rename(documentId, name) }
    fun setCategory(id: Long?) = op { repo.setCategory(documentId, id) }
    fun setVault(inVault: Boolean) = op(if (inVault) "Rangé dans le coffre-fort" else "Retiré du coffre-fort") {
        repo.setVault(documentId, inVault)
    }
    fun merge(sourceId: Long) = op("Documents fusionnés", showBusy = true) { repo.merge(documentId, sourceId) }
    suspend fun mergeCandidates(): List<Document> = repo.otherDocuments(documentId)

    fun deleteDocument() = op(showBusy = true) {
        repo.deleteDocument(documentId)
        _events.send(EditEvent.Closed)
    }

    fun exportPdf(target: Uri) = op("PDF enregistré", showBusy = true) { repo.exportPdf(documentId, target) }
    fun exportImages(tree: Uri, format: ExportFormat) = op(showBusy = true) {
        val n = repo.exportImages(documentId, tree, format)
        _events.send(EditEvent.Message("$n image(s) ${format.label} enregistrée(s)"))
    }

    fun share() = op(showBusy = true) { _events.send(EditEvent.Share(repo.prepareSharePdf(documentId))) }
    fun clearShareCache() = repo.purgeShareCache()
}

// ---------------- Bibliothèque ----------------

enum class Period(val label: String, val days: Int?) {
    ALL("Toutes dates", null), WEEK("7 derniers jours", 7), MONTH("30 derniers jours", 30), YEAR("12 derniers mois", 365)
}

class LibraryViewModel(repo: DocumentRepository) : ViewModel() {
    val query = MutableStateFlow("")
    val categoryId = MutableStateFlow<Long?>(null)
    val profile = MutableStateFlow<ScanProfile?>(null)
    val period = MutableStateFlow(Period.ALL)
    val categories: StateFlow<List<Category>> = repo.categories.stateIn(this, emptyList())

    val results: StateFlow<List<DocumentSummary>> = combine(
        repo.summaries(vault = false), query, categoryId, profile, period,
    ) { list, q, cat, prof, per ->
        val since = per.days?.let {
            Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -it) }.timeInMillis
        }
        val needle = q.trim()
        list.filter { s ->
            val d = s.document
            (cat == null || d.categoryId == cat) &&
                (prof == null || d.profile == prof) &&
                (since == null || d.createdAt >= since) &&
                (needle.isEmpty() ||
                    d.name.contains(needle, ignoreCase = true) ||
                    d.profile.label.contains(needle, ignoreCase = true) ||
                    s.categoryName?.contains(needle, ignoreCase = true) == true ||
                    formatDate(d.createdAt).contains(needle, ignoreCase = true))
        }
    }.stateIn(this, emptyList())
}

// ---------------- Coffre-fort ----------------

class VaultViewModel(repo: DocumentRepository) : ViewModel() {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    val items: StateFlow<List<DocumentSummary>> = repo.summaries(vault = true).stateIn(this, emptyList())

    fun unlock() { _unlocked.value = true }
    fun lock() { _unlocked.value = false }
}

// ---------------- Paramètres ----------------

class SettingsViewModel(
    val settings: SettingsRepository,
    private val pinManager: PinManager,
    private val lockController: LockController,
    private val repo: DocumentRepository,
) : ViewModel() {
    private val _hasPin = MutableStateFlow(pinManager.hasPin())
    val hasPin: StateFlow<Boolean> = _hasPin.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setPin(pin: String) {
        pinManager.setPin(pin)
        _hasPin.value = true
        settings.setLockEnabled(true)
        _message.value = "Code PIN enregistré, verrouillage activé"
    }

    fun disableLock() {
        settings.setLockEnabled(false)
        _message.value = "Verrouillage désactivé"
    }

    fun removePin() {
        pinManager.clearPin()
        settings.setLockEnabled(false)
        _hasPin.value = false
        _message.value = "Code PIN supprimé"
    }

    fun setTheme(mode: ThemeMode) = settings.setTheme(mode)
    fun setFormat(format: ExportFormat) = settings.setExportFormat(format)
    fun setQuality(quality: ImageQuality) = settings.setQuality(quality)
    fun setBiometric(enabled: Boolean) = settings.setBiometricEnabled(enabled)
    fun lockNow() = lockController.lockNow()

    fun wipeAll() = viewModelScope.launch {
        runCatching { repo.wipeAll() }
            .onSuccess { _message.value = "Tous les documents ont été effacés définitivement" }
            .onFailure { _message.value = "Effacement incomplet : ${it.message}" }
    }

    fun consumeMessage() { _message.value = null }
}
