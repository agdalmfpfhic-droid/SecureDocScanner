package com.securedoc.scanner.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.DocumentsContract
import android.util.LruCache
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.securedoc.scanner.data.db.AppDatabase
import com.securedoc.scanner.data.model.Category
import com.securedoc.scanner.data.model.Document
import com.securedoc.scanner.data.model.DocumentSummary
import com.securedoc.scanner.data.model.ExportFormat
import com.securedoc.scanner.data.model.Page
import com.securedoc.scanner.data.model.PageFilter
import com.securedoc.scanner.data.model.ScanProfile
import com.securedoc.scanner.data.model.SecureItem
import com.securedoc.scanner.data.model.SecureKind
import com.securedoc.scanner.imaging.BitmapIO
import com.securedoc.scanner.imaging.ImageFilters
import com.securedoc.scanner.pdf.PdfBuilder
import com.securedoc.scanner.security.EncryptedFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Point d'accès unique aux documents : base Room chiffrée + fichiers chiffrés.
 * Aucune méthode n'effectue d'accès réseau.
 */
class DocumentRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val store: EncryptedFileStore,
    private val settings: SettingsRepository,
) {
    private val documents = db.documentDao()
    private val pages = db.pageDao()
    private val categoryDao = db.categoryDao()
    private val secureItems = db.secureItemDao()

    /** Cache mémoire des aperçus déchiffrés (jamais écrits sur disque). */
    private val previewCache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    val categories: Flow<List<Category>> = categoryDao.observeAll()
    fun summaries(vault: Boolean): Flow<List<DocumentSummary>> = documents.observeSummaries(vault)
    fun document(id: Long): Flow<Document?> = documents.observe(id)
    fun pages(documentId: Long): Flow<List<Page>> = pages.observe(documentId)
    suspend fun otherDocuments(excludeId: Long): List<Document> = documents.all().filter { it.id != excludeId }

    // ---------- Création ----------

    suspend fun createDocument(
        uris: List<Uri>,
        profile: ScanProfile,
        name: String?,
        categoryId: Long?,
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val category = categoryId ?: categoryDao.byName(profile.defaultCategory)?.id
        val docId = documents.insert(
            Document(
                name = name?.trim()?.takeIf { it.isNotEmpty() } ?: defaultName(profile, now),
                profile = profile,
                categoryId = category,
                createdAt = now,
                updatedAt = now,
                inVault = profile.secureKind != null,
            )
        )
        profile.secureKind?.let { secureItems.upsert(SecureItem(documentId = docId, kind = it, addedAt = now)) }
        appendPages(docId, uris, profile.defaultFilter)
        docId
    }

    suspend fun addPages(documentId: Long, uris: List<Uri>) = withContext(Dispatchers.IO) {
        val doc = documents.get(documentId) ?: return@withContext
        appendPages(documentId, uris, doc.profile.defaultFilter)
        documents.touch(documentId)
    }

    private suspend fun appendPages(documentId: Long, uris: List<Uri>, filter: PageFilter) {
        var position = pages.maxPosition(documentId) + 1
        for (uri in uris) {
            val image = BitmapIO.normalize(context.contentResolver, uri, MAX_STORED_SIDE, settings.quality.value.jpeg)
            val file = store.newPageFile()
            store.write(file, image.bytes)
            pages.insert(
                Page(
                    documentId = documentId,
                    position = position++,
                    filePath = file.name,
                    filter = filter,
                    width = image.width,
                    height = image.height,
                )
            )
        }
        discardTemporary(uris)
    }

    /** Supprime les copies en clair laissées par ML Kit dans le cache de l'app. */
    fun discardTemporary(uris: List<Uri>) {
        val cacheRoot = context.cacheDir.canonicalPath
        uris.filter { it.scheme == "file" }.forEach { uri ->
            val file = uri.path?.let(::File) ?: return@forEach
            if (file.canonicalPath.startsWith(cacheRoot)) store.secureDelete(file)
        }
    }

    // ---------- Rendu ----------

    suspend fun render(page: Page, maxSide: Int): Bitmap = withContext(Dispatchers.Default) {
        val side = maxSide.coerceAtMost(MAX_RENDER_SIDE)
        val key = "${page.filePath}|${page.filter}|${page.rotation}|$side"
        previewCache.get(key)?.let { return@withContext it }
        val bytes = withContext(Dispatchers.IO) { store.read(store.fileFor(page.filePath)) }
        val bitmap = ImageFilters.apply(BitmapIO.rotate(BitmapIO.decodeSampled(bytes, side), page.rotation), page.filter)
        if (side <= PREVIEW_CACHE_MAX_SIDE) previewCache.put(key, bitmap)
        bitmap
    }

    /** Aperçu d'une image pas encore enregistrée (résultat de scan / galerie). */
    suspend fun loadPreview(uri: Uri, maxSide: Int): Bitmap = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Image illisible")
        BitmapIO.decodeSampled(bytes, maxSide)
    }

    // ---------- Édition ----------

    suspend fun rotate(page: Page, delta: Int) {
        pages.update(page.copy(rotation = ((page.rotation + delta) % 360 + 360) % 360))
        documents.touch(page.documentId)
    }

    suspend fun setFilter(page: Page, filter: PageFilter) {
        pages.update(page.copy(filter = filter))
        documents.touch(page.documentId)
    }

    suspend fun setFilterForAll(documentId: Long, filter: PageFilter) {
        pages.updateAll(pages.list(documentId).map { it.copy(filter = filter) })
        documents.touch(documentId)
    }

    suspend fun movePage(documentId: Long, from: Int, to: Int) {
        val list = pages.list(documentId).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        pages.updateAll(list.mapIndexed { i, p -> p.copy(position = i) })
        documents.touch(documentId)
    }

    suspend fun deletePage(page: Page) {
        withContext(Dispatchers.IO) { store.secureDelete(store.fileFor(page.filePath)) }
        pages.delete(page)
        pages.updateAll(pages.list(page.documentId).mapIndexed { i, p -> p.copy(position = i) })
        documents.touch(page.documentId)
    }

    suspend fun rename(documentId: Long, name: String) {
        val doc = documents.get(documentId) ?: return
        val clean = name.trim().takeIf { it.isNotEmpty() } ?: return
        documents.update(doc.copy(name = clean, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setCategory(documentId: Long, categoryId: Long?) {
        val doc = documents.get(documentId) ?: return
        documents.update(doc.copy(categoryId = categoryId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun setVault(documentId: Long, inVault: Boolean) = db.withTransaction {
        val doc = documents.get(documentId) ?: return@withTransaction
        documents.update(doc.copy(inVault = inVault, updatedAt = System.currentTimeMillis()))
        if (inVault) {
            secureItems.upsert(
                SecureItem(
                    documentId = documentId,
                    kind = doc.profile.secureKind ?: SecureKind.OTHER,
                    addedAt = System.currentTimeMillis(),
                )
            )
        } else {
            secureItems.deleteForDocument(documentId)
        }
    }

    /** Ajoute les pages de [sourceId] à la fin de [targetId], puis supprime la source. */
    suspend fun merge(targetId: Long, sourceId: Long) = db.withTransaction {
        var position = pages.maxPosition(targetId) + 1
        pages.updateAll(pages.list(sourceId).map { it.copy(documentId = targetId, position = position++) })
        secureItems.deleteForDocument(sourceId)
        documents.delete(sourceId)
        documents.touch(targetId)
    }

    /** Suppression sécurisée définitive d'un document et de toutes ses pages. */
    suspend fun deleteDocument(documentId: Long) {
        val list = pages.list(documentId)
        withContext(Dispatchers.IO) { list.forEach { store.secureDelete(store.fileFor(it.filePath)) } }
        db.withTransaction {
            pages.deleteForDocument(documentId)
            secureItems.deleteForDocument(documentId)
            documents.delete(documentId)
        }
        previewCache.evictAll()
    }

    /** Efface tous les documents, pages et fichiers temporaires. */
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        store.wipePages()
        store.purgeShareCache()
        db.withTransaction {
            pages.deleteAll()
            secureItems.deleteAll()
            documents.deleteAll()
        }
        previewCache.evictAll()
    }

    // ---------- Export (local uniquement) ----------

    suspend fun writePdf(documentId: Long, out: OutputStream) {
        val doc = documents.get(documentId) ?: error("Document introuvable")
        val list = pages.list(documentId)
        require(list.isNotEmpty()) { "Ce document n'a aucune page" }
        PdfBuilder.write(doc.profile, list.size, { i -> render(list[i], MAX_RENDER_SIDE) }, out)
    }

    /** Export PDF vers un fichier choisi par l'utilisateur (Storage Access Framework). */
    suspend fun exportPdf(documentId: Long, target: Uri) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(target, "wt") ?: error("Écriture impossible")
        out.use { writePdf(documentId, it) }
    }

    /** Export JPEG/PNG : une image par page, dans le dossier choisi par l'utilisateur. */
    suspend fun exportImages(documentId: Long, treeUri: Uri, format: ExportFormat): Int = withContext(Dispatchers.IO) {
        val doc = documents.get(documentId) ?: error("Document introuvable")
        val list = pages.list(documentId)
        val resolver = context.contentResolver
        val parent = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        list.forEachIndexed { i, page ->
            val bytes = BitmapIO.encode(render(page, MAX_RENDER_SIDE), format, settings.quality.value.jpeg)
            val name = "${safeFileName(doc.name)}_${i + 1}.${format.extension}"
            val uri = DocumentsContract.createDocument(resolver, parent, format.mime, name) ?: error("Création du fichier impossible")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Écriture impossible")
        }
        list.size
    }

    /** PDF temporaire pour un partage confirmé par l'utilisateur ; effacé après le partage. */
    suspend fun prepareSharePdf(documentId: Long): Uri = withContext(Dispatchers.IO) {
        val doc = documents.get(documentId) ?: error("Document introuvable")
        store.purgeShareCache()
        val file = File(store.shareDir, "${safeFileName(doc.name)}.pdf")
        FileOutputStream(file).use { writePdf(documentId, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun purgeShareCache() = store.purgeShareCache()

    private fun defaultName(profile: ScanProfile, time: Long): String =
        "${profile.label} ${SimpleDateFormat("dd-MM-yyyy HH'h'mm", Locale.FRANCE).format(Date(time))}"

    private fun safeFileName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80)

    private companion object {
        const val MAX_STORED_SIDE = 3000
        const val MAX_RENDER_SIDE = 2400
        const val PREVIEW_CACHE_MAX_SIDE = 1600
    }
}
