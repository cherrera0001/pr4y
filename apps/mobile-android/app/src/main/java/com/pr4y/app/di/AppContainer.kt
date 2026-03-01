package com.pr4y.app.di

import android.content.Context
import com.pr4y.app.data.local.AppDatabase
import com.pr4y.app.util.Pr4yLog

/**
 * Contenedor de dependencias de la sesión activa.
 * Refactorizado para evitar UninitializedPropertyAccessException.
 */
object AppContainer {

    private var _db: AppDatabase? = null
    
    val db: AppDatabase
        get() = _db ?: throw IllegalStateException("Bóveda no inicializada. Llame a AppContainer.init() primero.")

    private var currentUserId: String? = null

    fun isInitialized(): Boolean = _db != null

    fun init(context: Context, userId: String) {
        synchronized(this) {
            if (currentUserId == userId && _db != null && _db!!.isOpen) {
                return 
            }

            if (_db != null && currentUserId != null && currentUserId != userId) {
                val prevId = currentUserId!!
                Pr4yLog.i("AppContainer: Cambiando de usuario. Cerrando bóveda de $prevId.")
                try { _db!!.close() } catch (e: Exception) {
                    Pr4yLog.w("AppContainer: Error al cerrar DB previa: ${e.message}")
                }
                _db = null
            }

            currentUserId = userId
            _db = AppDatabase.getInstance(context, userId)
            Pr4yLog.i("AppContainer: Bóveda inicializada para usuario $userId")
        }
    }

    fun clearForLogout() {
        synchronized(this) {
            if (_db != null) {
                try {
                    _db!!.clearAllTables()
                    _db!!.close()
                    Pr4yLog.i("AppContainer: Bóveda cerrada y limpiada en logout")
                } catch (e: Exception) {
                    Pr4yLog.w("AppContainer: Fallo al cerrar bóveda: ${e.message}")
                } finally {
                    _db = null
                    currentUserId = null
                }
            }
        }
    }

    fun currentUserId(): String? = currentUserId
}
