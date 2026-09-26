package com.zerogram.di

import com.zerogram.domain.repository.ITelegramRepository
import com.zerogram.domain.repository.IVaultManager
import com.zerogram.data.repository.VaultManagerImpl
import com.zerogram.telegram.TDLibClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding all interfaces to their implementations.
 * This enforces the Clean Architecture layer separation.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindTelegramRepository(impl: TDLibClient): ITelegramRepository

    @Binds
    @Singleton
    abstract fun bindVaultManager(impl: VaultManagerImpl): IVaultManager
}
