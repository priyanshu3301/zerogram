package com.example.zerogram.telegram

import android.content.Context
import android.os.Build
import com.example.zerogram.core.logging.SecureLogger
import com.example.zerogram.domain.model.AppError
import com.example.zerogram.domain.model.AppResult
import com.example.zerogram.domain.repository.ITelegramRepository
import com.example.zerogram.domain.repository.TelegramAuthState
import com.example.zerogram.domain.repository.TelegramChannel
import com.example.zerogram.domain.repository.TelegramConnectionState
import com.example.zerogram.domain.repository.TelegramUser
import com.example.zerogram.domain.repository.UploadEvent
import com.example.zerogram.domain.repository.DownloadEvent
import com.example.zerogram.domain.repository.TelegramMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TDLib client implementation of [ITelegramRepository].
 * Wraps the TDLib JNI interface (libtdjni.so) for all Telegram operations.
 *
 * SECURITY: This class must NEVER log phone numbers, OTP codes, 2FA passwords,
 * API hashes, or any user-specific metadata. (FR-1.8, NFR-1.3)
 */
@Singleton
class TDLibClient @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ITelegramRepository, Client.ResultHandler {

    companion object {
        private var isLibraryLoaded = false
        
        fun loadLibrary() {
            if (isLibraryLoaded) return
            try {
                System.loadLibrary("tdjni")
                SecureLogger.d("TDLibClient", "libtdjni.so loaded successfully")
                isLibraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                SecureLogger.e("TDLibClient", "Failed to load libtdjni.so — TDLib unavailable")
            }
        }
    }

    private val _authState = MutableStateFlow(TelegramAuthState.LOADING)
    private val _connectionState = MutableStateFlow(TelegramConnectionState.CONNECTING)

    /** Broadcasts TDLib UpdateFile events to active upload/download collectors. */
    private val _fileUpdates = MutableSharedFlow<TdApi.UpdateFile>(extraBufferCapacity = 64)
    val fileUpdates = _fileUpdates.asSharedFlow()

    private val client: Client by lazy {
        loadLibrary()
        Client.create(this, null, null)
    }

    override suspend fun warmUp() {
        withContext(Dispatchers.IO) {
            loadLibrary()
            try {
                Client.execute(TdApi.SetLogVerbosityLevel(1))
            } catch (e: Exception) {
                SecureLogger.e("TDLibClient", "Failed to set TDLib log verbosity: ${e.message}")
            }
            client.send(TdApi.GetOption("version")) { }
        }
    }

    override fun observeAuthState(): Flow<TelegramAuthState> = _authState.asStateFlow()
    override fun observeConnectionState(): Flow<TelegramConnectionState> = _connectionState.asStateFlow()

    override fun onResult(result: TdApi.Object?) {
        when (result) {
            is TdApi.UpdateAuthorizationState -> handleAuthorizationState(result.authorizationState)
            is TdApi.UpdateConnectionState -> handleConnectionState(result.state)
            is TdApi.UpdateFile -> {
                // Broadcast to all active upload/download collectors
                _fileUpdates.tryEmit(result)
            }
        }
    }

    private fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> _authState.value = TelegramAuthState.WAIT_TDLIB_PARAMETERS
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = TelegramAuthState.WAIT_PHONE_NUMBER
            is TdApi.AuthorizationStateWaitCode -> _authState.value = TelegramAuthState.WAIT_CODE
            is TdApi.AuthorizationStateWaitPassword -> _authState.value = TelegramAuthState.WAIT_PASSWORD
            is TdApi.AuthorizationStateReady -> _authState.value = TelegramAuthState.READY
            is TdApi.AuthorizationStateLoggingOut -> _authState.value = TelegramAuthState.LOGGING_OUT
            is TdApi.AuthorizationStateClosed -> _authState.value = TelegramAuthState.CLOSED
            else -> _authState.value = TelegramAuthState.LOADING
        }
    }

    private fun handleConnectionState(state: TdApi.ConnectionState) {
        when (state) {
            is TdApi.ConnectionStateConnecting -> _connectionState.value = TelegramConnectionState.CONNECTING
            is TdApi.ConnectionStateReady -> _connectionState.value = TelegramConnectionState.CONNECTED
            is TdApi.ConnectionStateWaitingForNetwork -> _connectionState.value = TelegramConnectionState.WAITING_FOR_NETWORK
            else -> _connectionState.value = TelegramConnectionState.CONNECTING
        }
    }

    // --- Auth ---

    override suspend fun initializeTdlib(apiId: Int, apiHash: String): AppResult<Unit> {
        val databaseDir = File(context.filesDir, "tdlib")
        val filesDir = File(context.filesDir, "tdlib_files")
        databaseDir.mkdirs()
        filesDir.mkdirs()

        val parameters = TdApi.SetTdlibParameters()
        parameters.useTestDc = false
        parameters.databaseDirectory = databaseDir.absolutePath
        parameters.filesDirectory = filesDir.absolutePath
        parameters.useFileDatabase = true
        parameters.useChatInfoDatabase = true
        parameters.useMessageDatabase = true
        parameters.useSecretChats = false
        parameters.apiId = apiId
        parameters.apiHash = apiHash
        parameters.systemLanguageCode = "en"
        parameters.deviceModel = "Android"
        parameters.systemVersion = Build.VERSION.RELEASE
        parameters.applicationVersion = "1.0"

        return when (val result = client.sendSuspend<TdApi.Ok>(parameters)) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> {
                // If TDLib is already initialized, it replies with 400 "Unexpected setTdlibParameters".
                // We can safely ignore this error and consider it a success.
                if (result.error.message.contains("setTdlibParameters", ignoreCase = true)) {
                    AppResult.Success(Unit)
                } else {
                    result
                }
            }
        }
    }

    override suspend fun sendPhoneNumber(phoneNumber: String): AppResult<Unit> {
        val request = TdApi.SetAuthenticationPhoneNumber().apply {
            this.phoneNumber = phoneNumber
        }
        return client.sendSuspend<TdApi.Ok>(request).map { Unit }
    }

    override suspend fun checkAuthenticationCode(code: String): AppResult<Unit> {
        val request = TdApi.CheckAuthenticationCode().apply {
            this.code = code
        }
        return client.sendSuspend<TdApi.Ok>(request).map { Unit }
    }

    override suspend fun checkAuthenticationPassword(password: String): AppResult<Unit> {
        val request = TdApi.CheckAuthenticationPassword().apply {
            this.password = password
        }
        return client.sendSuspend<TdApi.Ok>(request).map { Unit }
    }

    override suspend fun logOut(): AppResult<Unit> {
        return client.sendSuspend<TdApi.Ok>(TdApi.LogOut()).map { Unit }
    }

    override suspend fun getCurrentUser(): AppResult<TelegramUser> {
        return client.sendSuspend<TdApi.User>(TdApi.GetMe()).map { user ->
            TelegramUser(
                id = user.id,
                firstName = user.firstName,
                lastName = user.lastName,
                phoneNumber = user.phoneNumber ?: "",
                isPremium = user.isPremium
            )
        }
    }

    // --- Channel management ---

    override suspend fun getOwnedChannels(): AppResult<List<TelegramChannel>> {
        val getChats = TdApi.GetChats().apply {
            chatList = TdApi.ChatListMain()
            limit = 100
        }
        return client.sendSuspend<TdApi.Chats>(getChats).map { chats ->
            val ownedChannels = mutableListOf<TelegramChannel>()
            for (chatId in chats.chatIds) {
                val chatResult = client.sendSuspend<TdApi.Chat>(TdApi.GetChat().apply { this.chatId = chatId })
                if (chatResult is AppResult.Success) {
                    val chat = chatResult.data
                    val type = chat.type
                    if (type is TdApi.ChatTypeSupergroup) {
                        val supergroupResult = client.sendSuspend<TdApi.Supergroup>(TdApi.GetSupergroup().apply { supergroupId = type.supergroupId })
                        if (supergroupResult is AppResult.Success && supergroupResult.data.status is TdApi.ChatMemberStatusCreator) {
                            ownedChannels.add(TelegramChannel(chat.id, chat.title, 0))
                        }
                    }
                }
            }
            ownedChannels
        }
    }

    override suspend fun createPrivateChannel(title: String): AppResult<TelegramChannel> {
        val request = TdApi.CreateNewSupergroupChat().apply {
            this.title = title
            isChannel = true
            isForum = false
        }
        return client.sendSuspend<TdApi.Chat>(request).map { chat ->
            TelegramChannel(chat.id, chat.title, 0)
        }
    }

    override suspend fun validateChannel(chatId: Long): AppResult<Boolean> {
        val result = client.sendSuspend<TdApi.Chat>(TdApi.GetChat().apply { this.chatId = chatId })
        return when (result) {
            is AppResult.Success -> {
                val type = result.data.type
                if (type is TdApi.ChatTypeSupergroup) {
                    val supergroupResult = client.sendSuspend<TdApi.Supergroup>(TdApi.GetSupergroup().apply { supergroupId = type.supergroupId })
                    if (supergroupResult is AppResult.Success) {
                        AppResult.Success(supergroupResult.data.status is TdApi.ChatMemberStatusCreator)
                    } else {
                        AppResult.Success(false)
                    }
                } else {
                    AppResult.Success(false)
                }
            }
            is AppResult.Failure -> AppResult.Success(false)
        }
    }

    // --- File transfer ---

    override fun uploadDocument(localPath: String, storageChatId: Long, caption: String?): Flow<UploadEvent> = callbackFlow {
        val document = TdApi.InputMessageDocument().apply {
            this.document = TdApi.InputDocument(TdApi.InputFileLocal(localPath), null, false)
            if (caption != null) {
                this.caption = TdApi.FormattedText(caption, emptyArray<TdApi.TextEntity>())
            }
        }
        val request = TdApi.SendMessage().apply {
            chatId = storageChatId
            inputMessageContent = document
        }

        var trackedFileId: Int? = null
        var sentMessageId: Long? = null
        var isCompleted = false

        // Collect file update events in a coroutine.
        // IMPORTANT: TDLib's native callback thread can emit an UpdateFile the instant the
        // SendMessage request is dispatched (this happens routinely for small/fast files).
        // _fileUpdates has replay = 0, so any event emitted while there are zero subscribers
        // is dropped forever — not buffered, regardless of extraBufferCapacity. We use
        // onSubscription + a CompletableDeferred so we can await actual subscription before
        // issuing the request below, closing that race window.
        val subscribed = CompletableDeferred<Unit>()
        val fileUpdateJob = launch {
            _fileUpdates.asSharedFlow()
                .onSubscription { subscribed.complete(Unit) }
                .collect { update ->
                    val file = update.file
                    val targetId = trackedFileId ?: return@collect
                    if (file.id != targetId) return@collect

                    // Emit progress
                    if (file.expectedSize > 0) {
                        trySend(UploadEvent.Progress(
                            uploadedBytes = file.remote.uploadedSize,
                            totalBytes = file.expectedSize
                        ))
                    }

                    // Check completion
                    if (file.remote.isUploadingCompleted) {
                        val msgId = sentMessageId
                        if (msgId != null) {
                            isCompleted = true
                            trySend(UploadEvent.Completed(
                                messageId = msgId,
                                remoteFileId = file.remote.id
                            ))
                            close()
                        }
                    }
                }
        }
        subscribed.await()

        // Send the message
        client.send(request) { result ->
            when (result) {
                is TdApi.Message -> {
                    sentMessageId = result.id
                    val content = result.content
                    if (content is TdApi.MessageDocument) {
                        trackedFileId = content.document.document.id
                        val file = content.document.document
                        if (file.remote.isUploadingCompleted) {
                            isCompleted = true
                            trySend(UploadEvent.Completed(
                                messageId = result.id,
                                remoteFileId = file.remote.id
                            ))
                            close()
                        }
                    }
                }
                is TdApi.Error -> {
                    val appError = if (result.code == 420) {
                        val waitSeconds = Regex("""(\d+)""").find(result.message)?.groupValues?.get(1)?.toIntOrNull() ?: 30
                        AppError.RateLimitError("Rate limited by Telegram", waitSeconds)
                    } else {
                        AppError.NetworkError("Upload failed: ${result.message}", result.code != 400)
                    }
                    trySend(UploadEvent.Failed(appError))
                    close()
                }
            }
        }

        awaitClose {
            fileUpdateJob.cancel()
            val msgId = sentMessageId
            if (msgId != null && !isCompleted) {
                client.send(TdApi.DeleteMessages(storageChatId, longArrayOf(msgId), true)) { }
            }
        }
    }

    override fun downloadDocument(telegramFileId: String, offset: Long, priority: Int): Flow<DownloadEvent> = callbackFlow {
        val getRemoteReq = TdApi.GetRemoteFile().apply {
            this.remoteFileId = telegramFileId
            this.fileType = null
        }
        val remoteFileRes = client.sendSuspend<TdApi.File>(getRemoteReq)
        if (remoteFileRes is AppResult.Failure) {
            trySend(DownloadEvent.Failed(AppError.UnknownError("Failed to resolve remote file ID: ${remoteFileRes.error.message}")))
            close()
            return@callbackFlow
        }
        val fileId = (remoteFileRes as AppResult.Success).data.id

        // Cancel any previous partial download for this file before starting fresh,
        // so TDLib doesn't silently ignore the new DownloadFile request.
        client.sendSuspend<TdApi.Ok>(TdApi.CancelDownloadFile(fileId, false))

        // IMPORTANT: TDLib's native callback thread can emit an UpdateFile the instant the
        // DownloadFile request is dispatched (this happens routinely for small/fast files —
        // 128KB-a few MB can finish before this coroutine is even scheduled). _fileUpdates has
        // replay = 0, so any event emitted while there are zero subscribers is dropped forever —
        // not buffered, regardless of extraBufferCapacity. We use onSubscription + a
        // CompletableDeferred so we can await actual subscription before issuing the request
        // below, closing that race window. Without this, the flow can silently hang until the
        // 5-minute timeout in TransferService fires, wrongly retrying/failing an already-finished
        // download and blocking the rest of the queue behind it.
        val subscribed = CompletableDeferred<Unit>()
        val fileUpdateJob = launch {
            _fileUpdates
                .onSubscription { subscribed.complete(Unit) }
                .collect { update ->
                    if (update.file.id == fileId) {
                        val local = update.file.local
                        if (local.isDownloadingCompleted) {
                            trySend(DownloadEvent.Completed(local.path))
                            close()
                        } else if (local.isDownloadingActive) {
                            trySend(DownloadEvent.Progress(local.downloadedSize.toLong(), update.file.expectedSize.toLong()))
                        }
                    }
                }
        }
        subscribed.await()

        // Defensive check: the file may already be fully downloaded locally (e.g. a leftover
        // cached copy from a prior attempt that raced as described above). If so, resolve
        // immediately instead of waiting on a DownloadFile round trip that may never signal it.
        val precheck = client.sendSuspend<TdApi.File>(TdApi.GetFile(fileId))
        if (precheck is AppResult.Success && precheck.data.local.isDownloadingCompleted) {
            trySend(DownloadEvent.Completed(precheck.data.local.path))
            close()
            return@callbackFlow
        }

        val request = TdApi.DownloadFile().apply {
            this.fileId = fileId
            // Priority 1 is the highest priority in TDLib.
            this.priority = priority
            this.offset = offset
            this.limit = 0
            this.synchronous = false
        }

        client.send(request) { result ->
            when (result) {
                is TdApi.Error -> {
                    val appError = if (result.code == 420) {
                        val waitSeconds = Regex("""(\d+)""").find(result.message)?.groupValues?.get(1)?.toIntOrNull() ?: 30
                        AppError.RateLimitError("Rate limited by Telegram", waitSeconds)
                    } else {
                        AppError.NetworkError("Download failed: ${result.message}", result.code != 400)
                    }
                    trySend(DownloadEvent.Failed(appError))
                    close()
                }
                is TdApi.File -> {
                    if (result.local.isDownloadingCompleted) {
                        trySend(DownloadEvent.Completed(result.local.path))
                        close()
                    }
                }
            }
        }

        awaitClose {
            fileUpdateJob.cancel()
            client.send(TdApi.CancelDownloadFile(fileId, false)) { }
        }
    }

    override suspend fun getFileDownloadedPrefixSize(telegramFileId: String, offset: Long): AppResult<Long> {
        val getRemoteReq = TdApi.GetRemoteFile().apply {
            this.remoteFileId = telegramFileId
            this.fileType = null
        }
        val remoteFileRes = client.sendSuspend<TdApi.File>(getRemoteReq)
        if (remoteFileRes is AppResult.Failure) {
            return AppResult.Failure(AppError.UnknownError("Failed to resolve remote file ID: ${remoteFileRes.error.message}"))
        }
        val fileId = (remoteFileRes as AppResult.Success).data.id

        val request = TdApi.GetFileDownloadedPrefixSize().apply {
            this.fileId = fileId
            this.offset = offset
        }
        return client.sendSuspend<TdApi.Count>(request).map { it.count.toLong() }
    }

    override suspend fun getLocalFilePath(telegramFileId: String): String? {
        val getRemoteReq = TdApi.GetRemoteFile().apply {
            this.remoteFileId = telegramFileId
            this.fileType = null
        }
        val remoteFileRes = client.sendSuspend<TdApi.File>(getRemoteReq)
        if (remoteFileRes is AppResult.Failure) return null
        val fileId = (remoteFileRes as AppResult.Success).data.id

        val request = TdApi.GetFile().apply {
            this.fileId = fileId
        }
        val result = client.sendSuspend<TdApi.File>(request)
        if (result is AppResult.Success) {
            val local = result.data.local
            if (local.isDownloadingCompleted) {
                return local.path.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    override suspend fun deleteLocalFile(telegramFileId: String): AppResult<Unit> {
        val remoteId = telegramFileId

        val getRemoteReq = TdApi.GetRemoteFile().apply {
            this.remoteFileId = remoteId
            this.fileType = null
        }
        val remoteFileRes = client.sendSuspend<TdApi.File>(getRemoteReq)
        if (remoteFileRes is AppResult.Failure) {
            return AppResult.Failure(AppError.UnknownError("Failed to resolve remote file ID for cache eviction: ${remoteFileRes.error.message}"))
        }

        val localFileId = (remoteFileRes as AppResult.Success).data.id

        val request = TdApi.DeleteFile().apply {
            this.fileId = localFileId
        }
        return client.sendSuspend<TdApi.Ok>(request).map { Unit }
    }

    override suspend fun optimizeStorage(): AppResult<Unit> {
        val request = TdApi.OptimizeStorage().apply {
            size = 0
            ttl = 86400
            count = 0
            immunityDelay = 3600
            fileTypes = arrayOf(TdApi.FileTypeDocument())
        }
        return client.sendSuspend<TdApi.Ok>(request).map { Unit }
    }

    override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>): AppResult<Unit> {
        val request = TdApi.DeleteMessages().apply {
            this.chatId = chatId
            this.messageIds = messageIds.toLongArray()
            revoke = true
        }
        return client.sendSuspend<TdApi.Ok>(request).map { Unit }
    }

    override suspend fun searchMessages(chatId: Long, query: String, limit: Int, onlyDocuments: Boolean): AppResult<List<TelegramMessage>> {
        val request = TdApi.SearchChatMessages().apply {
            this.chatId = chatId
            this.query = query
            this.limit = limit
            if (onlyDocuments) {
                this.filter = TdApi.SearchMessagesFilterDocument()
            }
        }
        return client.sendSuspend<TdApi.FoundChatMessages>(request).map { messages ->
            messages.messages.mapNotNull { it.toTelegramMessage() }
        }
    }

    override suspend fun getChatHistory(chatId: Long, fromMessageId: Long, limit: Int): AppResult<List<TelegramMessage>> {
        val request = TdApi.GetChatHistory().apply {
            this.chatId = chatId
            this.fromMessageId = fromMessageId
            this.limit = limit
        }
        return client.sendSuspend<TdApi.Messages>(request).map { messages ->
            messages.messages.mapNotNull { it.toTelegramMessage() }
        }
    }

    private fun TdApi.Message.toTelegramMessage(): TelegramMessage? {
        val content = this.content
        if (content !is TdApi.MessageDocument) return null
        return TelegramMessage(
            id = this.id,
            date = this.date,
            text = (content.caption?.text ?: ""),
            documentFileId = content.document.document.remote.id,
            documentFileName = content.document.fileName,
            documentSize = content.document.document.size.toLong()
        )
    }
}