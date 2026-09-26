package com.zerogram.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.zerogram.R
import com.zerogram.core.logging.SecureLogger
import com.zerogram.crypto.CryptoManager
import com.zerogram.data.local.ZerogramDatabase
import com.zerogram.data.local.entity.TransferJobEntity
import com.zerogram.domain.model.AppResult
import com.zerogram.domain.repository.ITelegramRepository
import com.zerogram.domain.repository.IVaultManager
import com.example.zerogram.domain.repository.UploadEvent
import com.example.zerogram.domain.repository.DownloadEvent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.io.File
import javax.inject.Inject
import kotlin.math.pow

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@AndroidEntryPoint
class TransferService : Service() {

    @Inject lateinit var database: ZerogramDatabase
    @Inject lateinit var telegramRepository: ITelegramRepository
    @Inject lateinit var cryptoManager: CryptoManager
    @Inject lateinit var vaultManager: IVaultManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val processingMutex = Mutex()
    private var isUploading = false
    private var isDownloading = false
    // Track last activity time to detect zombie workers that hold the flag
    @Volatile private var lastUploadActivityMs = 0L
    @Volatile private var lastDownloadActivityMs = 0L
    private val WORKER_STALE_TIMEOUT_MS = 6 * 60 * 1000L // 6 minutes (timeout is 5 min, +1 min buffer)

    companion object {
        const val CHANNEL_ID = "TransferServiceChannel"
        const val NOTIFICATION_ID = 101

        fun startService(context: Context) {
            val startIntent = Intent(context, TransferService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(startIntent)
            } else {
                context.startService(startIntent)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, createNotification("Checking transfers...", 0))
        }
        serviceScope.launch {
            processingMutex.withLock {
                checkAndStartJobs()
            }
        }
        return START_STICKY
    }

    private suspend fun checkAndStartJobs() {
        val vaultKey = vaultManager.getActiveKey()
        if (vaultKey == null) {
            stopForegroundServiceAndSelf()
            return
        }

        val config = database.vaultConfigDao().getVaultConfig()
        if (config == null || config.storageChatId == null) {
            stopForegroundServiceAndSelf()
            return
        }
        
        val storageChatId = config.storageChatId
        val now = System.currentTimeMillis()

        cleanupOrphanedCacheFiles()

        // Detect and force-reset zombie workers.
        // If a worker flag is true but hasn't reported activity in WORKER_STALE_TIMEOUT_MS,
        // it's a zombie (the coroutine hung or crashed without clearing the flag).
        if (isUploading && lastUploadActivityMs > 0 && (now - lastUploadActivityMs) > WORKER_STALE_TIMEOUT_MS) {
            SecureLogger.e("TransferService", "Upload worker appears stale (no activity for ${(now - lastUploadActivityMs) / 1000}s), force-resetting")
            isUploading = false
        }
        if (isDownloading && lastDownloadActivityMs > 0 && (now - lastDownloadActivityMs) > WORKER_STALE_TIMEOUT_MS) {
            SecureLogger.e("TransferService", "Download worker appears stale (no activity for ${(now - lastDownloadActivityMs) / 1000}s), force-resetting")
            isDownloading = false
        }

        var hasActiveWork = isUploading || isDownloading

        if (!isUploading) {
            val stuckUploads = database.transferJobDao().getActiveJobs("upload")
            for (job in stuckUploads) {
                database.transferJobDao().updateJobStatus(job.id, "queued")
            }
            val nextUpload = database.transferJobDao().getNextPendingJob("upload")
            if (nextUpload != null) {
                isUploading = true
                lastUploadActivityMs = now
                hasActiveWork = true
                serviceScope.launch {
                    try {
                        handleUpload(nextUpload, vaultKey, storageChatId)
                    } finally {
                        processingMutex.withLock {
                            isUploading = false
                            checkAndStartJobs()
                        }
                    }
                }
            }
        }

        if (!isDownloading) {
            val stuckDownloads = database.transferJobDao().getActiveJobs("download")
            for (job in stuckDownloads) {
                database.transferJobDao().updateJobStatus(job.id, "queued")
            }
            val nextDownload = database.transferJobDao().getNextPendingJob("download")
            if (nextDownload != null) {
                isDownloading = true
                lastDownloadActivityMs = now
                hasActiveWork = true
                serviceScope.launch {
                    try {
                        handleDownload(nextDownload, vaultKey)
                    } finally {
                        processingMutex.withLock {
                            isDownloading = false
                            checkAndStartJobs()
                        }
                    }
                }
            }
        }
        
        if (!hasActiveWork) {
            stopForegroundServiceAndSelf()
        }
    }

    private suspend fun cleanupOrphanedCacheFiles() {
        try {
            val files = cacheDir.listFiles() ?: return
            for (file in files) {
                if (file.name.startsWith("encrypted_")) {
                    val idPart = file.name.removePrefix("encrypted_").removeSuffix(".tmp")
                    if (idPart.isNotEmpty()) {
                        val job = database.transferJobDao().getJobById(idPart)
                        if (job == null || (job.status != "paused" && job.status != "queued" && job.status != "uploading")) {
                            file.delete()
                            SecureLogger.d("TransferService", "Deleted orphaned cache file: ${file.name}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SecureLogger.e("TransferService", "Error during cache cleanup", e)
        }
    }

    private fun stopForegroundServiceAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    private suspend fun handleUpload(job: TransferJobEntity, key: ByteArray, chatId: Long) {
        val fileEntity = database.fileDao().getFileById(job.fileId)
        if (fileEntity == null) {
            database.transferJobDao().updateJobStatus(job.id, "failed")
            return
        }
        
        // 2GB limit check (2 * 1024 * 1024 * 1024 bytes)
        val limit2GB = 2L * 1024 * 1024 * 1024
        if (job.totalBytes > limit2GB) {
            database.transferJobDao().updateJobStatus(job.id, "failed", System.currentTimeMillis())
            return
        }

        database.transferJobDao().updateJobStatus(job.id, "uploading")

        val encryptedFile = File(cacheDir, "encrypted_${job.id}")

        try {
            if (!encryptedFile.exists()) {
                updateNotification("Encrypting ${fileEntity.displayName}...", 0)

                if (job.sourcePath == null) {
                    database.transferJobDao().updateJobStatus(job.id, "failed", System.currentTimeMillis())
                    return
                }

                // Encrypt directly from the source path to a temporary file to save 1x cache storage
                val tempEncryptedFile = File(cacheDir, "encrypted_${job.id}.tmp")
                if (tempEncryptedFile.exists()) tempEncryptedFile.delete()
                
                var encryptionSuccess = false
                try {
                    val inputFile = File(job.sourcePath)
                    if (inputFile.exists() && inputFile.canRead()) {
                        val inputStream = java.io.FileInputStream(inputFile)
                        val encryptResult = cryptoManager.encryptStream(inputStream, tempEncryptedFile, key)
                        encryptionSuccess = encryptResult is AppResult.Success
                    }
                } catch (e: Exception) {
                    SecureLogger.e("TransferService", "Error opening input stream for encryption", e)
                }

                if (!encryptionSuccess) {
                    if (tempEncryptedFile.exists()) tempEncryptedFile.delete()
                    database.transferJobDao().updateJobStatus(job.id, "failed", System.currentTimeMillis())
                    return
                }
                
                // Rename to the final file only when encryption is fully completed
                if (!tempEncryptedFile.renameTo(encryptedFile)) {
                    if (tempEncryptedFile.exists()) tempEncryptedFile.delete()
                    database.transferJobDao().updateJobStatus(job.id, "failed")
                    return
                }
            } else {
                updateNotification("Resuming ${fileEntity.displayName}...", 0)
            }

            val maxRetries = 3
            var currentRetry = 0
            var lastError: String? = null
            var uploadSucceeded = false

            while (currentRetry <= maxRetries) {
                val currentJob = database.transferJobDao().getJobById(job.id)
                if (currentJob == null || currentJob.status == "paused" || currentJob.status == "canceled") {
                    return
                }

                if (currentRetry > 0) {
                    val backoffMs = 3000L * 3.0.pow((currentRetry - 1).toDouble()).toLong()
                    SecureLogger.d("TransferService", "Upload retry #$currentRetry for ${fileEntity.displayName} after ${backoffMs}ms")
                    updateNotification("Retrying ${fileEntity.displayName}... (attempt ${currentRetry + 1}/${maxRetries + 1})", 0)
                    database.transferJobDao().updateJobStatus(job.id, "queued")
                    delay(backoffMs)
                    
                    val recheckJob = database.transferJobDao().getJobById(job.id)
                    if (recheckJob == null || recheckJob.status == "paused" || recheckJob.status == "canceled") {
                        return
                    }
                }

                database.transferJobDao().updateJobStatus(job.id, "uploading")
                var lastProgressTime = 0L
                var retryNeeded = false

                try {
                    val uploadResult = withTimeoutOrNull(24 * 60 * 60 * 1000L) {
                        telegramRepository.uploadDocument(encryptedFile.absolutePath, chatId, "")
                            .collect { event ->
                                when (event) {
                                    is UploadEvent.Progress -> {
                                        val latestJob = database.transferJobDao().getJobById(job.id)
                                        if (latestJob == null || latestJob.status == "paused" || latestJob.status == "canceled") {
                                            throw CancellationException("Job canceled or removed")
                                        }

                                        val now = System.currentTimeMillis()
                                        if (now - lastProgressTime > 500) {
                                            val updatedJob = job.copy(
                                                progressBytes = event.uploadedBytes,
                                                totalBytes = event.totalBytes,
                                                status = "uploading",
                                                updatedAt = now
                                            )
                                            database.transferJobDao().updateJob(updatedJob)
                                            
                                            val progress = if (event.totalBytes > 0) (event.uploadedBytes * 100 / event.totalBytes).toInt() else 0
                                            updateNotification("Uploading ${fileEntity.displayName}...", progress)
                                            lastProgressTime = now
                                            lastUploadActivityMs = now
                                        }
                                    }
                                    is UploadEvent.Completed -> {
                                        val finishedJob = job.copy(
                                            status = "completed",
                                            progressBytes = job.totalBytes,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        database.transferJobDao().updateJob(finishedJob)
                                        
                                        val updatedFile = fileEntity.copy(
                                            telegramMessageId = event.messageId,
                                            telegramFileId = event.remoteFileId,
                                            uploadStatus = "completed",
                                            encryptedSizeBytes = encryptedFile.length(),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        database.fileDao().updateFile(updatedFile)
                                        
                                        val cleanupResult = telegramRepository.deleteLocalFile(event.remoteFileId)
                                        if (cleanupResult is AppResult.Failure) {
                                            SecureLogger.e("TransferService", "Failed to clean up uploaded file cache: ${cleanupResult.error.message}")
                                        } else {
                                            SecureLogger.d("TransferService", "Successfully cleaned up TDLib cache for completed upload")
                                        }
                                        
                                        vaultManager.syncDatabase()
                                        uploadSucceeded = true
                                    }
                                    is UploadEvent.Failed -> {
                                        val error = event.error
                                        if (error is com.example.zerogram.domain.model.AppError.RateLimitError) {
                                            SecureLogger.d("TransferService", "Rate limited, waiting ${error.retryAfterSeconds}s")
                                            updateNotification("Rate limited, waiting ${error.retryAfterSeconds}s...", 0)
                                            delay(error.retryAfterSeconds * 1000L)
                                            throw Exception("RATE_LIMIT_RETRY")
                                        }
                                        lastError = error.message
                                        throw Exception(error.message)
                                    }
                                }
                            }
                    }

                    if (uploadSucceeded) {
                        return
                    }

                    if (uploadResult == null) {
                        lastError = "Upload timed out"
                        SecureLogger.e("TransferService", "Upload timeout for ${fileEntity.displayName}")
                        retryNeeded = true
                    }

                } catch (e: Exception) {
                    if (e is CancellationException) {
                        return
                    }
                    lastError = e.message
                    SecureLogger.e("TransferService", "Upload attempt ${currentRetry + 1} failed: ${e.message}")
                    retryNeeded = true
                }
                
                if (retryNeeded) {
                    currentRetry++
                }
            }

            // All retries exhausted
            val failedJob = (database.transferJobDao().getJobById(job.id) ?: job).copy(
                status = "failed",
                retryCount = maxRetries + 1,
                lastError = lastError ?: "Upload failed after ${maxRetries + 1} attempts",
                updatedAt = System.currentTimeMillis()
            )
            database.transferJobDao().updateJob(failedJob)
        } finally {
            val currentJob = database.transferJobDao().getJobById(job.id)
            if (currentJob?.status != "paused") {
                if (encryptedFile.exists()) encryptedFile.delete()
                val tempEncryptedFile = File(cacheDir, "encrypted_${job.id}.tmp")
                if (tempEncryptedFile.exists()) tempEncryptedFile.delete()
            }
        }
    }

    private suspend fun handleDownload(job: TransferJobEntity, key: ByteArray) {
        val fileEntity = database.fileDao().getFileById(job.fileId)
        if (fileEntity == null || fileEntity.telegramFileId == null) {
            database.transferJobDao().updateJobStatus(job.id, "failed")
            return
        }

        val maxRetries = 3
        var currentRetry = 0
        var lastError: String? = null

        while (currentRetry <= maxRetries) {
            // Re-read job from DB on each attempt to get latest progressBytes/status
            val currentJob = database.transferJobDao().getJobById(job.id)
            if (currentJob == null || currentJob.status == "paused" || currentJob.status == "canceled") {
                return // User canceled/paused during retry wait
            }

            if (currentRetry > 0) {
                // Exponential backoff: 3s, 9s, 27s
                val backoffMs = 3000L * 3.0.pow((currentRetry - 1).toDouble()).toLong()
                SecureLogger.d("TransferService", "Download retry #$currentRetry for ${fileEntity.displayName} after ${backoffMs}ms")
                updateNotification("Retrying ${fileEntity.displayName}... (attempt ${currentRetry + 1}/${maxRetries + 1})", 0)
                database.transferJobDao().updateJobStatus(job.id, "queued")
                delay(backoffMs)

                // Re-check status after backoff wait
                val recheckJob = database.transferJobDao().getJobById(job.id)
                if (recheckJob == null || recheckJob.status == "paused" || recheckJob.status == "canceled") {
                    return
                }
            }

            database.transferJobDao().updateJobStatus(job.id, "downloading")

            try {
                var lastProgressTime = 0L
                var downloadSucceeded = false

                // 24-hour timeout prevents zombie downloads from blocking the queue forever, but allows large files
                val downloadResult = withTimeoutOrNull(24 * 60 * 60 * 1000L) {
                    telegramRepository.downloadDocument(fileEntity.telegramFileId, 0, 1)
                        .collect { event ->
                            when (event) {
                                is DownloadEvent.Progress -> {
                                    val latestJob = database.transferJobDao().getJobById(job.id)
                                    if (latestJob == null || latestJob.status == "paused" || latestJob.status == "canceled") {
                                        throw CancellationException("Job canceled or removed")
                                    }

                                    val now = System.currentTimeMillis()
                                    if (now - lastProgressTime > 500) {
                                        val updatedJob = currentJob.copy(
                                            progressBytes = event.downloadedBytes,
                                            totalBytes = event.totalBytes,
                                            status = "downloading",
                                            updatedAt = now
                                        )
                                        database.transferJobDao().updateJob(updatedJob)

                                        val progress = if (event.totalBytes > 0) (event.downloadedBytes * 100 / event.totalBytes).toInt() else 0
                                        updateNotification("Downloading ${fileEntity.displayName}...", progress)
                                        lastProgressTime = now
                                        lastDownloadActivityMs = now
                                    }
                                }
                                is DownloadEvent.Completed -> {
                                    // Update progress to 100% before decryption so UI doesn't look like download failed midway
                                    val completedJob = currentJob.copy(
                                        progressBytes = currentJob.totalBytes,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                    database.transferJobDao().updateJob(completedJob)

                                    // Decrypt
                                    updateNotification("Decrypting ${fileEntity.displayName}...", 100)

                                    val encryptedFile = File(event.localPath)
                                    var decryptResult: AppResult<Unit>? = null

                                    val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Zerogram")
                                    if (!downloadsDir.exists()) downloadsDir.mkdirs()
                                    val decryptedFile = File(downloadsDir, fileEntity.displayName)
                                    
                                    decryptResult = cryptoManager.decryptFile(encryptedFile, decryptedFile, key)
                                    
                                    if (decryptResult is AppResult.Failure && decryptedFile.exists()) {
                                        SecureLogger.e("TransferService", "Decryption failed, deleting file ${fileEntity.displayName}")
                                        decryptedFile.delete()
                                    }
                                    if (decryptResult is AppResult.Success) {
                                        val finishedJob = currentJob.copy(
                                            status = "completed",
                                            progressBytes = currentJob.totalBytes,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        database.transferJobDao().updateJob(finishedJob)

                                        val updatedFile = fileEntity.copy(
                                            uploadStatus = "completed",
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        database.fileDao().updateFile(updatedFile)

                                        // Clean up the local TDLib cached copy since the file has been successfully decrypted and saved
                                        val cleanupResult = telegramRepository.deleteLocalFile(fileEntity.telegramFileId)
                                        if (cleanupResult is AppResult.Failure) {
                                            SecureLogger.e("TransferService", "Failed to clean up downloaded file cache: ${cleanupResult.error.message}")
                                        } else {
                                            SecureLogger.d("TransferService", "Successfully cleaned up TDLib cache for completed download")
                                        }

                                        // Backup database to vault after successful download
                                        vaultManager.syncDatabase()
                                        downloadSucceeded = true
                                    } else {
                                        val errorMsg = (decryptResult as? AppResult.Failure)?.error?.message ?: "Decryption failed"
                                        lastError = errorMsg
                                        android.util.Log.e("DEBUG_ZEROGRAM", "Download Event Completed but decryption failed: $errorMsg")
                                        // Throw a specific exception to avoid retrying on decryption failure
                                        throw DecryptionException(errorMsg)
                                    }
                                }
                                is DownloadEvent.Failed -> {
                                    val error = event.error
                                    if (error is com.example.zerogram.domain.model.AppError.RateLimitError) {
                                        // FLOOD_WAIT: wait the required time, then retry
                                        SecureLogger.d("TransferService", "Rate limited, waiting ${error.retryAfterSeconds}s")
                                        updateNotification("Rate limited, waiting ${error.retryAfterSeconds}s...", 0)
                                        delay(error.retryAfterSeconds * 1000L)
                                        throw Exception("RATE_LIMIT_RETRY") // Break out to retry loop
                                    }
                                    lastError = error.message
                                    throw Exception(error.message)
                                }
                            }
                        }
                }

                if (downloadSucceeded) {
                    return // Success, we're done
                }

                if (downloadResult == null) {
                    // Timeout hit
                    lastError = "Download timed out"
                    SecureLogger.e("TransferService", "Download timeout for ${fileEntity.displayName}")
                    currentRetry++
                    continue
                }

                // If we reach here without success, fall through to retry
                currentRetry++

            } catch (e: Exception) {
                if (e is CancellationException) {
                    // User-initiated cancel — don't retry
                    return
                }
                lastError = e.message
                SecureLogger.e("TransferService", "Download attempt ${currentRetry + 1} failed: ${e.message}")
                
                if (e is DecryptionException) {
                    // Do not retry if it's a decryption failure (e.g. wrong key, corrupted file)
                    break
                }
                
                currentRetry++
            }
        }

        // All retries exhausted — mark as permanently failed
        val failedJob = (database.transferJobDao().getJobById(job.id) ?: job).copy(
            status = "failed",
            retryCount = maxRetries + 1,
            lastError = lastError ?: "Download failed after ${maxRetries + 1} attempts",
            updatedAt = System.currentTimeMillis()
        )
        database.transferJobDao().updateJob(failedJob)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Transfers",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String, progress: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Zerogram Transfers")
            .setContentText(content)
            .setOngoing(true)
            
        if (progress in 1..100) {
            builder.setProgress(100, progress, false)
        } else {
            builder.setProgress(0, 0, true)
        }
            
        return builder.build()
    }

    private fun updateNotification(content: String, progress: Int) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, createNotification(content, progress))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

class DecryptionException(message: String) : Exception(message)
