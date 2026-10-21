package com.securedoc.scanner.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Nom d'icône Material, résolu dans l'UI. */
    val icon: String,
    val isSystem: Boolean = true,
)

@Entity(
    tableName = "documents",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["id"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.SET_NULL,
    )],
    indices = [Index("categoryId"), Index("name"), Index("inVault")],
)
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val profile: ScanProfile,
    val categoryId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    /** true = rangé dans le Coffre-fort (masqué de la bibliothèque). */
    val inVault: Boolean = false,
)

/**
 * Une page = une image JPEG chiffrée (EncryptedFile) dans files/pages/.
 * Filtre et rotation sont non destructifs : appliqués au rendu.
 */
@Entity(
    tableName = "pages",
    foreignKeys = [ForeignKey(
        entity = Document::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("documentId")],
)
data class Page(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val position: Int,
    /** Nom du fichier chiffré (relatif au dossier pages). */
    val filePath: String,
    val filter: PageFilter = PageFilter.COLOR,
    val rotation: Int = 0,
    val width: Int,
    val height: Int,
)

/** Métadonnées d'un document du Coffre-fort. */
@Entity(
    tableName = "secure_items",
    foreignKeys = [ForeignKey(
        entity = Document::class,
        parentColumns = ["id"],
        childColumns = ["documentId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["documentId"], unique = true)],
)
data class SecureItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    val kind: SecureKind,
    val holderName: String? = null,
    val expiresAt: Long? = null,
    val note: String? = null,
    val addedAt: Long,
)

/** Projection pour les listes : document + couverture + nombre de pages. */
data class DocumentSummary(
    @Embedded val document: Document,
    val pageCount: Int,
    val coverPath: String?,
    val coverFilter: PageFilter?,
    val coverRotation: Int?,
    val categoryName: String?,
)

val DEFAULT_CATEGORIES = listOf(
    "Administratif" to "folder",
    "Banque" to "bank",
    "Identité" to "badge",
    "Travail" to "work",
    "Santé" to "health",
    "Autres" to "other",
)
