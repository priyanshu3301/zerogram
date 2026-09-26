package com.example.zerogram.domain.repository

import com.example.zerogram.domain.model.AppResult

interface IVaultManager {
    suspend fun checkDatabaseExists(chatId: Long): AppResult<Boolean>
    suspend fun createNewVault(chatId: Long, vaultName: String): AppResult<String>
    suspend fun initVaultOnExistingChannel(chatId: Long, vaultName: String = "My Vault"): AppResult<String>
    suspend fun unlockVault(chatId: Long, keyBase64: String): AppResult<Unit>
    suspend fun lockVault(): AppResult<Unit>
    suspend fun switchVault(): AppResult<Unit>
    suspend fun restoreSavedVault(): Boolean
    fun getActiveKey(): ByteArray?
    suspend fun getActiveVaultChatId(): Long?
    suspend fun syncDatabase(): AppResult<Unit>
}
