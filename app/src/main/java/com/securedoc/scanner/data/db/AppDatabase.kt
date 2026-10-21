package com.securedoc.scanner.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.securedoc.scanner.data.model.Category
import com.securedoc.scanner.data.model.DEFAULT_CATEGORIES
import com.securedoc.scanner.data.model.Document
import com.securedoc.scanner.data.model.Page
import com.securedoc.scanner.data.model.SecureItem
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Document::class, Page::class, Category::class, SecureItem::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun categoryDao(): CategoryDao
    abstract fun secureItemDao(): SecureItemDao

    companion object {
        private const val NAME = "securedoc.db"

        /**
         * Ouvre la base chiffrée par SQLCipher (AES-256).
         * La phrase de passe est aléatoire, générée une fois et stockée dans
         * EncryptedSharedPreferences, elle-même protégée par une clé Android Keystore.
         */
        fun build(context: Context, passphrase: ByteArray): AppDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        DEFAULT_CATEGORIES.forEach { (name, icon) ->
                            db.execSQL(
                                "INSERT INTO categories (name, icon, isSystem) VALUES (?, ?, 1)",
                                arrayOf(name, icon),
                            )
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        db.execSQL("PRAGMA foreign_keys = ON")
                        // Efface les données supprimées au lieu de les laisser dans les pages libres
                        db.query("PRAGMA secure_delete = ON").close()
                    }
                })
                .build()
        }
    }
}
