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
    version = 6,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun requestDao(): RequestDao
    abstract fun outboxDao(): OutboxDao
    abstract fun journalDao(): JournalDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun ledgerDao(): LedgerDao

    companion object {
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE requests ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE journal ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
            }
        }
        
        internal val MIGRATION_4_5 = object : Migration(4, 5) {
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

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE requests ADD COLUMN status TEXT NOT NULL DEFAULT 'PENDING'")
            }
        }

        /**
         * Nombre de la base de datos para un usuario específico.
         * Los UUIDs solo contienen caracteres alfanuméricos y guiones — seguros para nombres de archivo.
         */
        fun dbName(userId: String): String = "pr4y_${userId}.db"

        /**
         * Crea (o abre si ya existe) la base de datos privada del usuario.
         * Cada usuario tiene su propio archivo aislado — patrón TenantID.
         * No usa singleton: AppContainer gestiona el ciclo de vida de la instancia.
         */
        fun getInstance(context: Context, userId: String): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                dbName(userId),
            )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build()
        }
    }
}
