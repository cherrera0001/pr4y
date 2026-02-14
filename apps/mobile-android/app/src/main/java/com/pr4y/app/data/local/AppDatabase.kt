package com.pr4y.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pr4y.app.data.local.dao.JournalDao
import com.pr4y.app.data.local.dao.OutboxDao
import com.pr4y.app.data.local.dao.RequestDao
import com.pr4y.app.data.local.dao.SyncStateDao
import com.pr4y.app.data.local.entity.JournalEntity
import com.pr4y.app.data.local.entity.OutboxEntity
import com.pr4y.app.data.local.entity.RequestEntity
import com.pr4y.app.data.local.entity.SyncStateEntity

@Database(
    entities = [
        RequestEntity::class,
        JournalEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun requestDao(): RequestDao
    abstract fun outboxDao(): OutboxDao
    abstract fun journalDao(): JournalDao
    abstract fun syncStateDao(): SyncStateDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE journal ADD COLUMN encryptedPayloadB64 TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE requests ADD COLUMN encryptedPayloadB64 TEXT")
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
