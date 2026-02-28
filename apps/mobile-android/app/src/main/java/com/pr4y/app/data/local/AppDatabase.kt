package com.pr4y.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pr4y.app.data.local.dao.JournalDao
import com.pr4y.app.data.local.dao.LedgerDao
import com.pr4y.app.data.local.dao.OutboxDao
import com.pr4y.app.data.local.dao.RequestDao
import com.pr4y.app.data.local.dao.SyncStateDao
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.data.local.entity.LedgerEntity
import com.pr4y.app.data.local.entity.OutboxEntity
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.local.entity.SyncStateEntity

@Database(
    entities = [
        RequestEntity::class,
        JournalEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
        LedgerEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun requestDao(): RequestDao
    abstract fun outboxDao(): OutboxDao
    abstract fun journalDao(): JournalDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun ledgerDao(): LedgerDao

    companion object {
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE requests ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE journal ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }
        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `ledger` (
                        `id` TEXT NOT NULL, 
                        `userId` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `encryptedContent` TEXT NOT NULL, 
                        `iv` TEXT NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `isPublic` INTEGER NOT NULL DEFAULT 0, 
                        `contentHash` TEXT NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pr4y_db",
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
