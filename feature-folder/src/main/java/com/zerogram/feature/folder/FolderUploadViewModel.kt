package com.zerogram.feature.folder

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerogram.data.local.ZerogramDatabase
import com.zerogram.data.local.entity.FileEntity
import com.zerogram.data.local.entity.FolderEntity
import com.zerogram.data.local.entity.TransferJobEntity
import com.zerogram.domain.repository.IVaultManager
import com.zerogram.util.FolderScanner
import com.zerogram.util.ScanOptions
import com.zerogram.util.ScanResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException

sealed class FolderUploadState {
    object Idle : FolderUploadState()
    data class ScanOptionsSelection(val uri: Uri, val options: ScanOptions) : FolderUploadState()
    data class Scanning(val foldersScanned: Int, val filesFound: Int, val emptyFolders: Int, val totalSize: Long) : FolderUploadState()
    data class ScanComplete(val result: ScanResult, val targetParentId: String?) : FolderUploadState()
    object PreparingUpload : FolderUploadState()
    data class Error(val message: String) : FolderUploadState()
}

@HiltViewModel
class FolderUploadViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ZerogramDatabase,
    private val vaultManager: IVaultManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<FolderUploadState>(FolderUploadState.Idle)
    val uiState: StateFlow<FolderUploadState> = _uiState.asStateFlow()

    private val scanner = FolderScanner()
    private var scanJob: Job? = null

    fun onFolderSelected(uri: Uri, targetParentId: String?) {
        _uiState.value = FolderUploadState.ScanOptionsSelection(uri = uri, options = ScanOptions())
    }


    fun updateScanOptions(options: ScanOptions) {
        val currentState = _uiState.value
        if (currentState is FolderUploadState.ScanOptionsSelection) {
            _uiState.value = currentState.copy(options = options)
        }
    }

    fun startScan(targetParentId: String?) {
        val currentState = _uiState.value
        if (currentState !is FolderUploadState.ScanOptionsSelection) return

        val uri = currentState.uri
        val options = currentState.options

        val realPath = com.zerogram.util.UriUtils.getPath(context, uri)
        if (realPath == null) {
            _uiState.value = FolderUploadState.Error("Could not resolve folder path. Please select the folder from Internal Storage instead of Shortcuts.")
            return
        }

        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.value = FolderUploadState.Scanning(0, 0, 0, 0L)
            try {
                val result = scanner.scanPath(realPath, options) { folders, files, emptyFolders, size ->
                    _uiState.value = FolderUploadState.Scanning(folders, files, emptyFolders, size)
                }
                _uiState.value = FolderUploadState.ScanComplete(result, targetParentId)
            } catch (e: CancellationException) {
                _uiState.value = FolderUploadState.Idle
            } catch (e: Exception) {
                _uiState.value = FolderUploadState.Error("Scan failed: ${e.message} Please select from Internal Storage instead of Shortcuts.")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _uiState.value = FolderUploadState.Idle
    }

    fun commitUpload() {
        val currentState = _uiState.value
        if (currentState !is FolderUploadState.ScanComplete) return

        val result = currentState.result
        val targetParentId = currentState.targetParentId

        _uiState.value = FolderUploadState.PreparingUpload

        viewModelScope.launch {
            try {
                val vaultKey = vaultManager.getActiveKey()
                if (vaultKey == null) {
                    _uiState.value = FolderUploadState.Error("Vault is locked. Unlock vault before uploading.")
                    return@launch
                }

                val now = System.currentTimeMillis()
                
                // Map tempIds to real UUIDs for DB
                val tempToRealFolderId = mutableMapOf<String, String>()
                val foldersToInsert = mutableListOf<FolderEntity>()
                
                for (tempFolder in result.folders) {
                    val realId = UUID.randomUUID().toString()
                    tempToRealFolderId[tempFolder.tempId] = realId
                }

                for (tempFolder in result.folders) {
                    val realId = tempToRealFolderId[tempFolder.tempId]!!
                    // If parentTempId is null, it's the root of the scanned tree, so attach it to targetParentId
                    val realParentId = if (tempFolder.parentTempId == null) {
                        targetParentId
                    } else {
                        tempToRealFolderId[tempFolder.parentTempId]
                    }

                    foldersToInsert.add(
                        FolderEntity(
                            id = realId,
                            name = tempFolder.name,
                            parentId = realParentId,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }

                val filesToInsert = mutableListOf<FileEntity>()
                val jobsToInsert = mutableListOf<TransferJobEntity>()

                for (tempFile in result.files) {
                    val fileId = UUID.randomUUID().toString()
                    val jobId = UUID.randomUUID().toString()
                    
                    val realFolderId = tempToRealFolderId[tempFile.parentTempId]

                    filesToInsert.add(
                        FileEntity(
                            id = fileId,
                            folderId = realFolderId,
                            displayName = tempFile.name,
                            mimeType = tempFile.mimeType,
                            sizeBytes = tempFile.sizeBytes,
                            encryptedSizeBytes = null,

                            checksum = null,
                            telegramMessageId = null,
                            telegramFileId = null,
                            uploadStatus = "pending",
                            createdAt = now,
                            updatedAt = now
                        )
                    )

                    jobsToInsert.add(
                        TransferJobEntity(
                            id = jobId,
                            fileId = fileId,
                            type = "upload",
                            status = "queued",
                            progressBytes = 0L,
                            totalBytes = tempFile.sizeBytes,
                            retryCount = 0,
                            lastError = null,
                            sourcePath = tempFile.path,
                            createdAt = now,
                            updatedAt = now
                        )
                    )
                }

                // Atomic transaction
                database.withTransaction {
                    if (foldersToInsert.isNotEmpty()) {
                        database.folderDao().insertFolders(foldersToInsert)
                    }
                    if (filesToInsert.isNotEmpty()) {
                        database.fileDao().insertFiles(filesToInsert)
                    }
                    if (jobsToInsert.isNotEmpty()) {
                        database.transferJobDao().insertJobs(jobsToInsert)
                    }
                }

                val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            context.startForegroundService(intent)
                _uiState.value = FolderUploadState.Idle

            } catch (e: Exception) {
                _uiState.value = FolderUploadState.Error("Upload preparation failed: ${e.message}")
            }
        }
    }

    fun dismissError() {
        _uiState.value = FolderUploadState.Idle
    }
}
