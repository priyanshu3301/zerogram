package com.zerogram.domain.repository

import com.zerogram.domain.model.AppError
import com.zerogram.domain.model.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for all Telegram/TDLib interactions.
 * Implemented by [com.zerogram.telegram.TDLibClient].
 * (NFR-5.2 — Telegram integration behind interface)
 */
interface ITelegramRepository {

    // --- Auth ---

    /**
     * Emits the current TDLib authorization state.
     */
    fun observeAuthState(): Flow<TelegramAuthState>

    /** Pre-warms the TDLib client off the main thread. */
    suspend fun warmUp()

    /** Provides TDLib parameters including api_id and api_hash (from user, per OQ-04). */
    suspend fun initializeTdlib(apiId: Int, apiHash: String): AppResult<Unit>

    /** Sends the phone number to TDLib. Triggers WaitCode state. */
    suspend fun sendPhoneNumber(phoneNumber: String): AppResult<Unit>

    /** Submits the OTP code received by the user. */
    suspend fun checkAuthenticationCode(code: String): AppResult<Unit>

    /** Submits the 2FA password. */
    suspend fun checkAuthenticationPassword(password: String): AppResult<Unit>

    /** Logs the user out and destroys the local TDLib session. */
    suspend fun logOut(): AppResult<Unit>

    /** Returns the current user's Telegram ID and isPremium status. */
    suspend fun getCurrentUser(): AppResult<TelegramUser>

    // --- Channel management ---

    /** Searches all channels owned by the authenticated user. */
    suspend fun getOwnedChannels(): AppResult<List<TelegramChannel>>

    /**
     * Creates a new private channel with the given title.
     */
    suspend fun createPrivateChannel(title: String = "My Vault"): AppResult<TelegramChannel>

    /** Verifies the given chat ID is accessible and the user is the owner/admin. */
    suspend fun validateChannel(chatId: Long): AppResult<Boolean>

    // --- File transfer ---

    /**
     * Uploads a file from [localPath] to [storageChatId].
     * Emits [UploadEvent] updates: Progress, Completed, or Failed.
     */
    fun uploadDocument(
        localPath: String,
        storageChatId: Long,
        caption: String? = null
    ): Flow<UploadEvent>

    /**
     * Downloads the file identified by [telegramFileId].
     * Emits [DownloadEvent] updates: Progress, Completed (with local path), or Failed.
     */
    fun downloadDocument(telegramFileId: String, offset: Long = 0, priority: Int = 1): Flow<DownloadEvent>

    /**
     * Returns the contiguous downloaded prefix size starting from [offset].
     */
    suspend fun getFileDownloadedPrefixSize(telegramFileId: String, offset: Long): AppResult<Long>

    /**
     * Returns the absolute path to the local cached file for [telegramFileId].
     */
    suspend fun getLocalFilePath(telegramFileId: String): String?

    /**
     * Deletes the given local file from TDLib's internal cache.
     */
    suspend fun deleteLocalFile(telegramFileId: String): AppResult<Unit>

    /**
     * Asks TDLib to optimize its storage, clearing out old cached files.
     */
    suspend fun optimizeStorage(): AppResult<Unit>

    /**
     * Deletes the given message IDs from the storage channel.
     */
    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>): AppResult<Unit>

    /** Checks current network connectivity state as seen by TDLib. */
    fun observeConnectionState(): Flow<TelegramConnectionState>

    /** Searches for messages in a chat matching a text query. */
    suspend fun searchMessages(chatId: Long, query: String, limit: Int = 10, onlyDocuments: Boolean = false): AppResult<List<TelegramMessage>>

    /** Gets the last N messages in a chat for raw recovery scanning. */
    suspend fun getChatHistory(chatId: Long, fromMessageId: Long = 0, limit: Int = 100): AppResult<List<TelegramMessage>>
}

data class TelegramMessage(
    val id: Long,
    val date: Int,
    val text: String,
    val documentFileId: String?,
    val documentFileName: String?,
    val documentSize: Long
)

// --- Supporting types ---

enum class TelegramAuthState {
    LOADING,
    WAIT_TDLIB_PARAMETERS,
    WAIT_PHONE_NUMBER,
    WAIT_CODE,
    WAIT_PASSWORD,
    READY,
    LOGGING_OUT,
    CLOSED,
    ERROR
}

data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val isPremium: Boolean
)

data class TelegramChannel(
    val chatId: Long,
    val title: String,
    val memberCount: Int
)

enum class TelegramConnectionState {
    CONNECTING,
    CONNECTED,
    UPDATING,
    WAITING_FOR_NETWORK,
    CONNECTING_TO_PROXY
}

/**
 * Events emitted during a file upload via [ITelegramRepository.uploadDocument].
 */
sealed class UploadEvent {
    /** Periodic progress update. */
    data class Progress(val uploadedBytes: Long, val totalBytes: Long) : UploadEvent()
    /** Upload completed successfully with Telegram identifiers. */
    data class Completed(val messageId: Long, val remoteFileId: String) : UploadEvent()
    /** Upload failed. */
    data class Failed(val error: AppError) : UploadEvent()
}

/**
 * Events emitted during a file download via [ITelegramRepository.downloadDocument].
 */
sealed class DownloadEvent {
    /** Periodic progress update. */
    data class Progress(val downloadedBytes: Long, val totalBytes: Long) : DownloadEvent()
    /** Download completed successfully to TDLib cache. */
    data class Completed(val localPath: String) : DownloadEvent()
    /** Download failed. */
    data class Failed(val error: AppError) : DownloadEvent()
}
