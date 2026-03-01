package com.pr4y.app.di

import android.content.Context
import com.pr4y.app.data.local.AppDatabase
import com.pr4y.app.util.Pr4yLog

/**
 * Contenedor de dependencias de la sesión activa.
 *
 * Patrón TenantID: cada usuario tiene su propia base de datos aislada.
 * La instancia se crea en `init(context, userId)` tras autenticación y se destruye en logout.
 *
 * Garantías de privacidad:
 * - Un usuario nunca abre la DB de otro usuario.
 * - Al cambiar de usuario, el archivo de la bóveda anterior se elimina del disco.
 * - En logout, `clearAllTables()` limpia los datos en memoria antes de que la UI navegue.
 */
object AppContainer {

    lateinit var db: AppDatabase
        private set

    private var currentUserId: String? = null

    /**
     * Inicializa (o reutiliza) la bóveda del usuario autenticado.
     *
     * Si el userId es diferente al de la sesión anterior:
     *   1. Cierra la DB anterior.
     *   2. Elimina su archivo del disco (privacidad absoluta).
     *   3. Abre la DB del nuevo usuario (o la crea si no existe).
     */
    fun init(context: Context, userId: String) {
        synchronized(this) {
            if (currentUserId == userId && ::db.isInitialized && db.isOpen) {
                return // Misma sesión — no hacer nada
            }

            // Cambio de usuario: destruir bóveda anterior
            if (::db.isInitialized && currentUserId != null && currentUserId != userId) {
                val prevId = currentUserId!!
                Pr4yLog.i("AppContainer: cambiando de usuario. Eliminando bóveda de $prevId.")
                try { db.close() } catch (e: Exception) {
                    Pr4yLog.w("AppContainer: error al cerrar DB previa: ${e.message}")
                }
                val deleted = context.applicationContext.deleteDatabase(AppDatabase.dbName(prevId))
                Pr4yLog.i("AppContainer: bóveda anterior eliminada: $deleted")
            }

            currentUserId = userId
            db = AppDatabase.getInstance(context, userId)
            Pr4yLog.i("AppContainer: bóveda inicializada para usuario $userId")
        }
    }

    /**
     * Limpia todas las tablas de la sesión actual (seguro con flows activos).
     * Llamar en logout antes de navegar a la pantalla de inicio de sesión.
     */
    fun clearForLogout() {
        if (::db.isInitialized) {
            try {
                db.clearAllTables()
                Pr4yLog.i("AppContainer: tablas limpiadas en logout")
            } catch (e: Exception) {
                Pr4yLog.w("AppContainer: clearForLogout falló: ${e.message}")
            }
        }
    }

    /** userId de la sesión activa, o null si no hay sesión. */
    fun currentUserId(): String? = currentUserId
}
