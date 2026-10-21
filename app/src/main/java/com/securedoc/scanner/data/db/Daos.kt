package com.securedoc.scanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.securedoc.scanner.data.model.Category
import com.securedoc.scanner.data.model.Document
import com.securedoc.scanner.data.model.DocumentSummary
import com.securedoc.scanner.data.model.Page
import com.securedoc.scanner.data.model.SecureItem
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query(
        """
        SELECT d.*,
          (SELECT COUNT(*) FROM pages p WHERE p.documentId = d.id) AS pageCount,
          (SELECT p.filePath FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverPath,
          (SELECT p.filter FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverFilter,
          (SELECT p.rotation FROM pages p WHERE p.documentId = d.id ORDER BY p.position LIMIT 1) AS coverRotation,
          c.name AS categoryName
        FROM documents d
        LEFT JOIN categories c ON c.id = d.categoryId
        WHERE d.inVault = :vault
        ORDER BY d.updatedAt DESC
        """
    )
    fun observeSummaries(vault: Boolean): Flow<List<DocumentSummary>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observe(id: Long): Flow<Document?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun get(id: Long): Document?

    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    suspend fun all(): List<Document>

    @Insert
    suspend fun insert(document: Document): Long

    @Update
    suspend fun update(document: Document)

    @Query("UPDATE documents SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM documents")
    suspend fun deleteAll()
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position")
    fun observe(documentId: Long): Flow<List<Page>>

    @Query("SELECT * FROM pages WHERE documentId = :documentId ORDER BY position")
    suspend fun list(documentId: Long): List<Page>

    @Query("SELECT * FROM pages")
    suspend fun all(): List<Page>

    @Query("SELECT COALESCE(MAX(position), -1) FROM pages WHERE documentId = :documentId")
    suspend fun maxPosition(documentId: Long): Int

    @Insert
    suspend fun insert(page: Page): Long

    @Update
    suspend fun update(page: Page)

    @Update
    suspend fun updateAll(pages: List<Page>)

    @Delete
    suspend fun delete(page: Page)

    @Query("DELETE FROM pages WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)

    @Query("DELETE FROM pages")
    suspend fun deleteAll()
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY id")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Category?
}

@Dao
interface SecureItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SecureItem)

    @Query("SELECT * FROM secure_items WHERE documentId = :documentId")
    suspend fun forDocument(documentId: Long): SecureItem?

    @Query("DELETE FROM secure_items WHERE documentId = :documentId")
    suspend fun deleteForDocument(documentId: Long)

    @Query("DELETE FROM secure_items")
    suspend fun deleteAll()
}
