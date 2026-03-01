package com.pr4y.app.di

import com.pr4y.app.data.local.AppDatabase
import com.pr4y.app.data.local.dao.JournalDao
import com.pr4y.app.data.local.dao.LedgerDao
import com.pr4y.app.data.local.dao.OutboxDao
import com.pr4y.app.data.local.dao.RequestDao
import com.pr4y.app.data.local.dao.SyncStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Módulo de base de datos para Hilt.
 *
 * IMPORTANTE — Patrón TenantID:
 * La base de datos es por usuario. AppContainer.init(context, userId) la inicializa
 * tras autenticación. Aquí delegamos a AppContainer.db para que Hilt siempre
 * obtenga la bóveda del usuario activo.
 *
 * Sin @Singleton intencional: no cachear la instancia a nivel de Hilt —
 * AppContainer gestiona el ciclo de vida y garantiza la instancia correcta.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    fun provideDatabase(): AppDatabase = AppContainer.db

    @Provides
    fun provideRequestDao(db: AppDatabase): RequestDao = db.requestDao()

    @Provides
    fun provideOutboxDao(db: AppDatabase): OutboxDao = db.outboxDao()

    @Provides
    fun provideJournalDao(db: AppDatabase): JournalDao = db.journalDao()

    @Provides
    fun provideSyncStateDao(db: AppDatabase): SyncStateDao = db.syncStateDao()

    @Provides
    fun provideLedgerDao(db: AppDatabase): LedgerDao = db.ledgerDao()
}
