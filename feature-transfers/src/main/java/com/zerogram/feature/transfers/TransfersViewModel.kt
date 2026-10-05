package com.zerogram.feature.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerogram.core.logging.SecureLogger
import com.zerogram.data.local.ZerogramDatabase
import com.zerogram.data.local.dao.TransferJobWithFile
import com.zerogram.domain.repository.ITelegramRepository
import com.zerogram.domain.model.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import java.io.File

@HiltViewModel
class TransfersViewModel @Inject constructor(
    private val context: android.app.Application,
    private val database: ZerogramDatabase,
    private val telegramRepository: ITelegramRepository
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    private fun sortJobs(list: List<TransferJobWithFile>): List<TransferJobWithFile> {
        return list.sortedWith(compareBy<TransferJobWithFile> { 
            when (it.job.status) {
                "uploading", "downloading" -> 0
                "queued", "paused" -> 1
                else -> 2
            }
        }.thenByDescending { it.job.createdAt })
    }

    val uploads: StateFlow<List<TransferJobWithFile>> = database.transferJobDao().observeJobsWithFilesByType("upload")
        .map { sortJobs(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<TransferJobWithFile>> = database.transferJobDao().observeJobsWithFilesByType("download")
        .map { sortJobs(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun pauseJob(jobId: String) {
        viewModelScope.launch {
            database.transferJobDao().updateJobStatus(jobId, "paused")
        }
    }

    fun resumeJob(jobId: String) {
        viewModelScope.launch {
            // Setting it to queued will allow TransferService to pick it up again
            database.transferJobDao().updateJobStatus(jobId, "queued")
            val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    fun retryJob(jobId: String) {
        resumeJob(jobId)
    }

    fun cancelJob(jobId: String) {
        viewModelScope.launch {
            database.transferJobDao().updateJobStatus(jobId, "canceled")
            _uiEvents.emit("Transfer canceled")
        }
    }

    fun pauseAll(type: String) {
        viewModelScope.launch {
            database.transferJobDao().updateJobsStatusByTypeAndCurrentStatuses(
                type = type,
                targetStatus = "paused",
                currentStatuses = listOf("uploading", "downloading", "queued")
            )
            _uiEvents.emit("Paused all $type transfers")
        }
    }

    fun resumeAll(type: String) {
        viewModelScope.launch {
            database.transferJobDao().updateJobsStatusByTypeAndCurrentStatuses(
                type = type,
                targetStatus = "queued",
                currentStatuses = listOf("paused", "failed", "canceled")
            )
            val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _uiEvents.emit("Resumed all $type transfers")
        }
    }

    fun clearCompleted(type: String) {
        viewModelScope.launch {
            database.transferJobDao().deleteJobsByTypeAndStatuses(
                type = type,
                statuses = listOf("completed", "canceled")
            )
            _uiEvents.emit("Cleared completed transfers")
        }
    }

    fun pauseJobs(jobIds: Set<String>) {
        viewModelScope.launch {
            database.transferJobDao().updateJobStatuses(jobIds.toList(), "paused")
            _uiEvents.emit("Paused ${jobIds.size} transfers")
        }
    }

    fun resumeJobs(jobIds: Set<String>) {
        viewModelScope.launch {
            database.transferJobDao().updateJobStatuses(jobIds.toList(), "queued")
            val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _uiEvents.emit("Resumed ${jobIds.size} transfers")
        }
    }

    fun retryJobs(jobIds: Set<String>) {
        resumeJobs(jobIds)
    }

    fun cancelJobs(jobIds: Set<String>) {
        viewModelScope.launch {
            database.transferJobDao().updateJobStatuses(jobIds.toList(), "canceled")
            _uiEvents.emit("Canceled ${jobIds.size} transfers")
        }
    }

    fun removeJobs(jobIds: Set<String>, deleteFromVault: Boolean, deleteFromStorage: Boolean) {
        viewModelScope.launch {
            var filesDeleted = 0
            jobIds.forEach { jobId ->
                val job = database.transferJobDao().getJobById(jobId)
                if (job != null) {
                    val file = database.fileDao().getFileById(job.fileId)
                    if (file != null) {
                        if (deleteFromVault && job.type == "upload") {
                            database.fileDao().moveFilesToTrash(listOf(file.id), System.currentTimeMillis())
                            filesDeleted++
                        }
                        if (deleteFromStorage && job.type == "download") {
                            val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Zerogram")
                            val localFile = File(downloadsDir, file.displayName)
                            if (localFile.exists()) {
                                localFile.delete()
                                filesDeleted++
                            }
                        }
                        
                        // Cleanup TDLib cache for removed transfers.
                        // If it's a download, we clean up the partial/completed cached copy.
                        // If it's an upload, we clean up any cached copy TDLib created.
                        val tFileId = file.telegramFileId
                        if (tFileId != null) {
                            val cleanupResult = telegramRepository.deleteLocalFile(tFileId)
                            if (cleanupResult is AppResult.Failure) {
                                SecureLogger.e("TransfersViewModel", "Failed to clean up file cache on removal: ${cleanupResult.error.message}")
                            } else {
                                SecureLogger.d("TransfersViewModel", "Successfully cleaned up TDLib cache for removed job")
                            }
                        }
                    }
                    database.transferJobDao().deleteJob(jobId)
                }
            }
            if (filesDeleted > 0) {
                _uiEvents.emit("Removed ${jobIds.size} items and deleted $filesDeleted files.")
            } else {
                _uiEvents.emit("Removed ${jobIds.size} items.")
            }
        }
    }

    suspend fun getLocationPath(fileId: String, type: String): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (type == "download") {
                val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Zerogram")
                return@withContext downloadsDir.absolutePath
            } else {
                val file = database.fileDao().getFileById(fileId) ?: return@withContext "Unknown"
                var currentFolderId = file.folderId
                val pathParts = mutableListOf<String>()
                while (currentFolderId != null) {
                    val folder = database.folderDao().getFolderById(currentFolderId)
                    if (folder != null) {
                        pathParts.add(0, folder.name)
                        currentFolderId = folder.parentId
                    } else {
                        break
                    }
                }
                pathParts.add(0, "All files")
                return@withContext pathParts.joinToString(" / ")
            }
        }
    }

    suspend fun getFileFolderId(fileId: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            database.fileDao().getFileById(fileId)?.folderId
        }
    }
}
