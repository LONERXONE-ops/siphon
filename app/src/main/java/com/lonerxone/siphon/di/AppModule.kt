package com.lonerxone.siphon.di

import com.lonerxone.siphon.data.network.NetworkMonitor
import com.lonerxone.siphon.data.network.NetworkMonitorImpl
import com.lonerxone.siphon.data.repository.DownloadRepository
import com.lonerxone.siphon.data.repository.DownloadRepositoryImpl
import com.lonerxone.siphon.data.storage.StorageManager
import com.lonerxone.siphon.data.storage.StorageManagerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindStorageManager(impl: StorageManagerImpl): StorageManager

    @Binds
    @Singleton
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: NetworkMonitorImpl): NetworkMonitor
}
