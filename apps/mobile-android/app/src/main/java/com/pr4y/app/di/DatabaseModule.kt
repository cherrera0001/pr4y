package com.pr4y.app.di

import android.content.Context
import com.pr4y.app.data.local.AppDatabase
import com.pr4y.app.data.local.dao.JournalDao
import com.pr4y.app.data.local.dao.LedgerDao
import com.pr4y.app.data.local.dao.OutboxDao
import com.pr4y.app.data.local.dao.RequestDao
import com.pr4y.app.data.local.dao.SyncStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

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
