package com.pr4y.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_DB = "pr4y_migration_test"

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    // ── MIGRATION 3 → 4 ──────────────────────────────────────────────────────

    @Test
    fun migration3To4_addsUserIdToRequests() {
        // Crear DB en versión 3 con una fila de prueba (sin userId)
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO requests VALUES('req-1', 'Oración de prueba', 'Cuerpo', 1000, 2000, 0, NULL)"
            )
        }

        // Ejecutar y validar la migración
        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            val cursor = db.query("SELECT userId FROM requests WHERE id = 'req-1'")
            assertTrue("Debe existir la fila migrada", cursor.moveToFirst())
            // ALTER TABLE ADD COLUMN con DEFAULT '' → fila existente hereda cadena vacía
            assertEquals("", cursor.getString(0))
            cursor.close()
        }
    }

    @Test
    fun migration3To4_addsUserIdToJournal() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO journal VALUES('jrn-1', 'Contenido de diario', 1000, 2000, 0, NULL)"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            val cursor = db.query("SELECT userId FROM journal WHERE id = 'jrn-1'")
            assertTrue("Debe existir la fila migrada", cursor.moveToFirst())
            assertEquals("", cursor.getString(0))
            cursor.close()
        }
    }

    @Test
    fun migration3To4_preservesAllExistingColumns() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO requests VALUES('req-2', 'Título', 'Cuerpo', 1111, 2222, 1, 'encB64==')"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 4, true, AppDatabase.MIGRATION_3_4).use { db ->
            val cursor = db.query(
                "SELECT id, title, body, createdAt, updatedAt, synced, encryptedPayloadB64, userId FROM requests WHERE id = 'req-2'"
            )
            assertTrue(cursor.moveToFirst())
            assertEquals("req-2", cursor.getString(0))
            assertEquals("Título", cursor.getString(1))
            assertEquals("Cuerpo", cursor.getString(2))
            assertEquals(1111L, cursor.getLong(3))
            assertEquals(2222L, cursor.getLong(4))
            assertEquals(1, cursor.getInt(5))
            assertEquals("encB64==", cursor.getString(6))
            assertEquals("", cursor.getString(7))   // userId DEFAULT ''
            cursor.close()
        }
    }

    // ── MIGRATION 4 → 5 ──────────────────────────────────────────────────────

    @Test
    fun migration4To5_createsLedgerTable() {
        helper.createDatabase(TEST_DB, 4).use { /* DB vacía en v4 */ }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            val cursor = db.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='ledger'"
            )
            assertTrue("La tabla ledger debe existir tras la migración", cursor.moveToFirst())
            assertEquals("ledger", cursor.getString(0))
            cursor.close()
        }
    }

    @Test
    fun migration4To5_ledgerTableHasCorrectColumns() {
        helper.createDatabase(TEST_DB, 4).use { }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            // Insertar una fila real para verificar que todos los campos existen y aceptan datos
            db.execSQL(
                """INSERT INTO ledger VALUES(
                    'led-1', 'user-1', 'Título diario',
                    'encryptedContent==', 'iv==',
                    1000, 2000, 0, 'sha256hash=='
                )"""
            )
            val cursor = db.query("SELECT id, userId, isPublic, contentHash FROM ledger WHERE id='led-1'")
            assertTrue(cursor.moveToFirst())
            assertEquals("led-1", cursor.getString(0))
            assertEquals("user-1", cursor.getString(1))
            assertEquals(0, cursor.getInt(2))          // isPublic DEFAULT 0
            assertEquals("sha256hash==", cursor.getString(3))
            cursor.close()
        }
    }

    @Test
    fun migration4To5_preservesRequestsAndJournal() {
        helper.createDatabase(TEST_DB, 4).use { db ->
            db.execSQL(
                "INSERT INTO requests VALUES('req-3', 'Oración', NULL, 1000, 2000, 0, NULL, 'usr-42')"
            )
            db.execSQL(
                "INSERT INTO journal VALUES('jrn-2', 'Diario', 1000, 2000, 0, NULL, 'usr-42')"
            )
        }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, AppDatabase.MIGRATION_4_5).use { db ->
            val reqCursor = db.query("SELECT userId FROM requests WHERE id='req-3'")
            assertTrue(reqCursor.moveToFirst())
            assertEquals("usr-42", reqCursor.getString(0))
            reqCursor.close()

            val jrnCursor = db.query("SELECT userId FROM journal WHERE id='jrn-2'")
            assertTrue(jrnCursor.moveToFirst())
            assertEquals("usr-42", jrnCursor.getString(0))
            jrnCursor.close()
        }
    }

    // ── MIGRACIÓN COMPLETA 3 → 5 ─────────────────────────────────────────────

    @Test
    fun fullMigration3To5_preservesDataAndCreatesLedger() {
        helper.createDatabase(TEST_DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO requests VALUES('req-full', 'Pedido completo', 'Por mi familia', 5000, 6000, 1, 'enc==')"
            )
            db.execSQL(
                "INSERT INTO journal VALUES('jrn-full', 'Entrada de diario', 5000, 6000, 0, NULL)"
            )
            db.execSQL(
                "INSERT INTO outbox VALUES('req-full', 'prayer_request', 1, 'enc==', 5000, 5000)"
            )
        }

        helper.runMigrationsAndValidate(
            TEST_DB, 5, true,
            AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5
        ).use { db ->
            // Requests: datos preservados, userId en ''
            val reqCursor = db.query(
                "SELECT title, body, userId FROM requests WHERE id='req-full'"
            )
            assertTrue(reqCursor.moveToFirst())
            assertEquals("Pedido completo", reqCursor.getString(0))
            assertEquals("Por mi familia", reqCursor.getString(1))
            assertEquals("", reqCursor.getString(2))
            reqCursor.close()

            // Journal: datos preservados
            val jrnCursor = db.query("SELECT content, userId FROM journal WHERE id='jrn-full'")
            assertTrue(jrnCursor.moveToFirst())
            assertEquals("Entrada de diario", jrnCursor.getString(0))
            assertEquals("", jrnCursor.getString(1))
            jrnCursor.close()

            // Outbox: intacto
            val outboxCursor = db.query("SELECT type FROM outbox WHERE recordId='req-full'")
            assertTrue(outboxCursor.moveToFirst())
            assertEquals("prayer_request", outboxCursor.getString(0))
            outboxCursor.close()

            // Ledger: tabla nueva y vacía
            val ledgerCursor = db.query("SELECT COUNT(*) FROM ledger")
            assertTrue(ledgerCursor.moveToFirst())
            assertEquals(0, ledgerCursor.getInt(0))
            ledgerCursor.close()
        }
    }
}
