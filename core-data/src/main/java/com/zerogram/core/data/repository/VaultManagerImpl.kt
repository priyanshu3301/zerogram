package com.zerogram.data.repository

import android.content.Context
import com.zerogram.core.logging.SecureLogger
import com.zerogram.crypto.CryptoManager
import com.zerogram.data.local.ZerogramDatabase
import com.zerogram.data.local.entity.VaultConfigEntity
import com.zerogram.domain.model.AppError
import com.zerogram.domain.model.AppResult
import com.zerogram.domain.repository.CredentialsManager
import com.zerogram.domain.repository.ITelegramRepository
import com.zerogram.domain.repository.IVaultManager
import com.zerogram.domain.repository.UploadEvent
import com.zerogram.domain.repository.DownloadEvent
import com.zerogram.domain.repository.TelegramMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val telegramRepository: ITelegramRepository,
    private val cryptoManager: CryptoManager,
    private val credentialsManager: CredentialsManager,
    private val database: ZerogramDatabase
) : IVaultManager {

    private var activeKey: ByteArray? = null

    override fun getActiveKey(): ByteArray? = activeKey

    override suspend fun getActiveVaultChatId(): Long? = withContext(Dispatchers.IO) {
        if (activeKey != null) {
            return@withContext database.vaultConfigDao().getVaultConfig()?.storageChatId
        }
        return@withContext null
    }

    override suspend fun checkDatabaseExists(chatId: Long): AppResult<Boolean> = withContext(Dispatchers.IO) {
        // Try getChatHistory first as it's instant and reliable for recent uploads
        val historyResult = telegramRepository.getChatHistory(chatId, 0, 50)
        if (historyResult is AppResult.Success) {
            val exists = historyResult.data.any { 
                it.text == "database.encrypt" || it.documentFileName == "database.encrypt" 
            }
            if (exists) return@withContext AppResult.Success(true)
        }

        // Fallback to searching for documents
        return@withContext when (val searchResult = telegramRepository.searchMessages(chatId, "", 10, true)) {
            is AppResult.Success -> {
                val exists = searchResult.data.any { 
                    it.text == "database.encrypt" || it.documentFileName == "database.encrypt" 
                }
                AppResult.Success(exists)
            }
            is AppResult.Failure -> searchResult
        }
    }

    override suspend fun createNewVault(chatId: Long, vaultName: String): AppResult<String> {
        val channelResult = telegramRepository.createPrivateChannel(vaultName)
        if (channelResult is AppResult.Failure) {
            return AppResult.Failure(channelResult.error)
        }
        val newChatId = (channelResult as AppResult.Success).data.chatId
        return initVaultOnExistingChannel(newChatId, vaultName)
    }

    override suspend fun initVaultOnExistingChannel(chatId: Long, vaultName: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            // Clear existing local database tables instead of closing the database
            val dbFile = context.getDatabasePath(ZerogramDatabase.DATABASE_NAME)
            if (dbFile.exists()) {
                database.clearAllTables()
            }

            // Generate Key
            val vaultKey = cryptoManager.generateVaultKey()
            val vaultKeyBase64 = cryptoManager.encodeKeyBase64(vaultKey)

            // Force Room to create the database by inserting VaultConfig
            val userId = credentialsManager.getApiId()?.toLong() ?: 0L
            database.vaultConfigDao().insertOrUpdateConfig(
                VaultConfigEntity(
                    telegramUserId = userId,
                    storageChatId = chatId,
                    keyVersion = 1,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    vaultName = vaultName
                )
            )

            // Force checkpoint of WAL so all data is in the main db file
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }

            // Encrypt the database file
            val encryptedDbFile = File(context.cacheDir, "database.encrypt")
            val encryptResult = cryptoManager.encryptFile(dbFile, encryptedDbFile, vaultKey)
            if (encryptResult is AppResult.Failure) {
                return@withContext AppResult.Failure(encryptResult.error)
            }

            // Upload the encrypted database
            val uploadSuccess = uploadFile(encryptedDbFile.absolutePath, chatId, "database.encrypt")
            if (!uploadSuccess) {
                return@withContext AppResult.Failure(AppError.NetworkError("Failed to upload database.encrypt"))
            }

            // Cleanup
            encryptedDbFile.delete()

            // Save storage chat ID locally
            // Note: In real app, we should add saveStorageChatId to CredentialsManager
            // For now, this is tracked in VaultConfig locally.
            activeKey = vaultKey
            credentialsManager.saveVaultKey(vaultKeyBase64)

            return@withContext AppResult.Success(vaultKeyBase64)
        } catch (e: Exception) {
            return@withContext AppResult.Failure(AppError.UnknownError("Failed to initialize vault: ${e.message}"))
        }
    }

    override suspend fun unlockVault(chatId: Long, keyBase64: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val key = cryptoManager.decodeKeyBase64(keyBase64)
                ?: return@withContext AppResult.Failure(AppError.UnknownError("Invalid key format"))

            if (!cryptoManager.isValidKey(key)) {
                return@withContext AppResult.Failure(AppError.UnknownError("This vault key is from an older version of the app and is no longer compatible. Please create a new vault."))
            }

            if (activeKey != null) {
                val currentConfig = database.vaultConfigDao().getVaultConfig()
                if (currentConfig != null && currentConfig.storageChatId == chatId) {
                    val savedKeyBase64 = credentialsManager.getVaultKey()
                    if (savedKeyBase64 == keyBase64) {
                        return@withContext AppResult.Success(Unit)
                    }
                }
            }

            var dbMessage: TelegramMessage? = null

            // Try history first
            val historyResult = telegramRepository.getChatHistory(chatId, 0, 50)
            if (historyResult is AppResult.Success) {
                dbMessage = historyResult.data
                    .filter { it.text == "database.encrypt" || it.documentFileName == "database.encrypt" }
                    .maxByOrNull { it.date }
            }

            // Fallback to search for documents
            if (dbMessage == null) {
                val searchResult = telegramRepository.searchMessages(chatId, "", 10, true)
                if (searchResult is AppResult.Success) {
                    dbMessage = searchResult.data
                        .filter { it.text == "database.encrypt" || it.documentFileName == "database.encrypt" }
                        .maxByOrNull { it.date }
                }
            }

            if (dbMessage == null || dbMessage.documentFileId == null) {
                return@withContext AppResult.Failure(AppError.UnknownError("database.encrypt not found in this channel"))
            }

            val fileId = dbMessage.documentFileId!!

            // Download file
            val downloadedFilePath = downloadFile(fileId)
                ?: return@withContext AppResult.Failure(AppError.NetworkError("Failed to download database.encrypt"))

            val encryptedDbFile = File(downloadedFilePath)
            val tempDbFile = File(context.cacheDir, "temp_db.sqlite")
            val decryptResult = cryptoManager.decryptFile(encryptedDbFile, tempDbFile, key)
            
            // Delete the downloaded encrypted file immediately after decryption
            encryptedDbFile.delete()
            telegramRepository.deleteLocalFile(fileId)

            if (decryptResult is AppResult.Failure) {
                return@withContext AppResult.Failure(decryptResult.error)
            }

            // Always close + replace + restart, regardless of whether dbFile already
            // existed on disk. We used to branch on dbFile.exists() ("isReplacingExisting")
            // to decide whether a process restart was needed, but that check is unreliable:
            // the Room singleton can get an empty zerogram_vault.db created out from under us
            // by any other component that queries it (e.g. stats/home screens), so the file
            // can "exist" even when no real vault was ever unlocked in this run. Rather than
            // trying to guess, we unconditionally do the safe sequence: close Room's
            // connection, remove any old file, drop in the freshly decrypted one, then do a
            // full process restart so Room's singleton is guaranteed to open the new file
            // fresh with no other component racing to open/migrate it at the same time.
            val dbFile = context.getDatabasePath(ZerogramDatabase.DATABASE_NAME)

            // Ensure parent directory exists before copying
            dbFile.parentFile?.mkdirs()

            database.openHelper.close()
            if (dbFile.exists()) {
                dbFile.delete()
            }
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()

            // Copy file robustly instead of renameTo
            tempDbFile.copyTo(dbFile, overwrite = true)
            tempDbFile.delete()

            credentialsManager.saveVaultKey(keyBase64)

            // Restart app process to safely re-initialize Room's database singleton
            // against the newly-swapped-in file.
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Runtime.getRuntime().exit(0)
            return@withContext AppResult.Success(Unit)
        } catch (e: Exception) {
            return@withContext AppResult.Failure(AppError.UnknownError("Failed to unlock vault: ${e.message}"))
        }
    }

    override suspend fun lockVault(): AppResult<Unit> = withContext(Dispatchers.IO) {
        database.openHelper.close()
        val dbFile = context.getDatabasePath(ZerogramDatabase.DATABASE_NAME)
        if (dbFile.exists()) {
            dbFile.delete()
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
        }
        credentialsManager.clearVaultKey()
        activeKey = null
        
        telegramRepository.optimizeStorage()
        
        return@withContext AppResult.Success(Unit)
    }

    override suspend fun restoreSavedVault(): Boolean = withContext(Dispatchers.IO) {
        try {
            val savedKeyBase64 = credentialsManager.getVaultKey() ?: return@withContext false
            val key = cryptoManager.decodeKeyBase64(savedKeyBase64) ?: return@withContext false
            
            // Ensure the key is a valid Tink keyset (prevents crashing with old legacy keys)
            if (!cryptoManager.isValidKey(key)) {
                credentialsManager.clearVaultKey()
                return@withContext false
            }
            
            // Ensure DB exists locally before accepting the saved key
            val dbFile = context.getDatabasePath(ZerogramDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                credentialsManager.clearVaultKey()
                return@withContext false
            }

            // Verify local DB is accessible and valid
            val config = database.vaultConfigDao().getVaultConfig()
            if (config == null) {
                credentialsManager.clearVaultKey()
                return@withContext false
            }
            
            activeKey = key
            return@withContext true
        } catch (e: Exception) {
            SecureLogger.e("VaultManagerImpl", "Failed to restore saved vault: ${e.message}")
            credentialsManager.clearVaultKey()
            return@withContext false
        }
    }

    override suspend fun switchVault(): AppResult<Unit> {
        return lockVault()
    }

    override suspend fun syncDatabase(): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val vaultKey = activeKey ?: return@withContext AppResult.Failure(AppError.UnknownError("No active key"))
            val chatId = getActiveVaultChatId() ?: return@withContext AppResult.Failure(AppError.UnknownError("No active vault chat ID"))
            
            // Checkpoint WAL and prepare the database file for upload
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
            
            val dbFile = context.getDatabasePath(ZerogramDatabase.DATABASE_NAME)
            if (!dbFile.exists()) {
                return@withContext AppResult.Failure(AppError.UnknownError("Database file not found"))
            }

            val encryptedDbFile = File(context.cacheDir, "database.encrypt")
            val encryptResult = cryptoManager.encryptFile(dbFile, encryptedDbFile, vaultKey)
            if (encryptResult is AppResult.Failure) {
                return@withContext AppResult.Failure(encryptResult.error)
            }

            val uploadSuccess = uploadFile(encryptedDbFile.absolutePath, chatId, "database.encrypt")
            encryptedDbFile.delete()

            if (!uploadSuccess) {
                return@withContext AppResult.Failure(AppError.NetworkError("Failed to upload database.encrypt to vault"))
            }

            AppResult.Success(Unit)
        } catch (e: Exception) {
            AppResult.Failure(AppError.UnknownError("Failed to sync database: ${e.message}"))
        }
    }

    private suspend fun uploadFile(filePath: String, chatId: Long, caption: String): Boolean {
        return try {
            val event = telegramRepository.uploadDocument(filePath, chatId, caption)
                .first { it is UploadEvent.Completed || it is UploadEvent.Failed }
            event is UploadEvent.Completed
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadFile(fileId: String): String? {
        // First check if it's already downloaded
        val existingPath = telegramRepository.getLocalFilePath(fileId)
        if (existingPath != null) {
            if (File(existingPath).exists()) {
                return existingPath
            } else {
                // TDLib thinks it has the file, but it was deleted (e.g., it was the original upload file).
                // We must clear TDLib's local file reference so it can be re-downloaded correctly.
                telegramRepository.deleteLocalFile(fileId)
            }
        }

        return try {
            val event = telegramRepository.downloadDocument(fileId, 0, 1)
                .first { it is DownloadEvent.Completed || it is DownloadEvent.Failed }
            (event as? DownloadEvent.Completed)?.localPath
        } catch (e: Exception) {
            null
        }
    }
}
