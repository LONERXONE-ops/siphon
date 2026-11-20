package com.lonerxone.siphon.di

import android.content.Context
import androidx.room.Room
import com.lonerxone.siphon.data.db.SiphonDatabase
import com.lonerxone.siphon.data.db.dao.DownloadDao
import com.lonerxone.siphon.data.db.dao.WaitlistRuleDao
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
    fun provideDatabase(@ApplicationContext context: Context): SiphonDatabase {
        return Room.databaseBuilder(
            context,
            SiphonDatabase::class.java,
            SiphonDatabase.DATABASE_NAME
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideDownloadDao(database: SiphonDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    fun provideWaitlistRuleDao(database: SiphonDatabase): WaitlistRuleDao {
        return database.waitlistRuleDao()
    }
}
