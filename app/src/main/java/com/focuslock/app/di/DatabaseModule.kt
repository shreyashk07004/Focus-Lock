package com.focuslock.app.di

import android.content.Context
import androidx.room.Room
import com.focuslock.app.data.db.AppDatabase
import com.focuslock.app.data.db.BlockedAppDao
import com.focuslock.app.data.db.DailyUsageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // No destructive-migration fallback: user rules must survive upgrades, so any schema change
    // has to ship a real Migration.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideBlockedAppDao(db: AppDatabase): BlockedAppDao = db.blockedAppDao()

    @Provides
    fun provideDailyUsageDao(db: AppDatabase): DailyUsageDao = db.dailyUsageDao()
}
