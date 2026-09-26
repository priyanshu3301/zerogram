package com.example.zerogram.ui.folder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerogram.data.local.ZerogramDatabase
import com.example.zerogram.data.local.entity.FileEntity
import com.example.zerogram.data.local.entity.TransferJobEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.zerogram.R
import com.example.zerogram.crypto.CryptoManager
import com.example.zerogram.domain.model.AppResult
import com.example.zerogram.domain.repository.IVaultManager
import com.example.zerogram.service.TransferService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SelectionDetails(
    val title: String,
    val isMultiple: Boolean,
    val name: String? = null,
    val dateModified: String? = null,
    val sizeText: String,
    val location: String? = null,
    val itemsText: String? = null
)

data class Breadcrumb(
    val id: String?,
    val name: String
)

@HiltViewModel
class FolderViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ZerogramDatabase,
    private val vaultManager: IVaultManager,
    private val cryptoManager: CryptoManager
) : ViewModel() {

    private val _breadcrumbs = MutableStateFlow<List<Breadcrumb>>(listOf(Breadcrumb(null, "All files")))
    val breadcrumbs: StateFlow<List<Breadcrumb>> = _breadcrumbs.asStateFlow()

    private val _currentFolderId = MutableStateFlow<String?>(null)
    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(com.example.zerogram.ui.search.SortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<com.example.zerogram.ui.search.SortOrder> = _sortOrder.asStateFlow()

    val filesAndFolders: StateFlow<List<FileItemData>> = _currentFolderId.flatMapLatest { folderId ->
        combine(
            database.folderDao().getFoldersWithCountByParentId(folderId),
            database.fileDao().getFilesByFolderId(folderId),
            _searchQuery,
            _sortOrder
        ) { folders, files, query, sort ->
            val items = mutableListOf<FileItemData>()

            folders.forEach { folder ->
                val itemsText = if (folder.itemCount == 1) "1 item" else "${folder.itemCount} items"
                    items.add(
                        FileItemData(
                            id = folder.id,
                            name = folder.name,
                            date = com.example.zerogram.util.FormatUtils.formatDate(folder.createdAt),
                            size = itemsText,
                            iconRes = R.drawable.ic_file_folder_icon,
                            isFolder = true,
                            timestamp = folder.createdAt,
                            sizeBytes = 0L
                        )
                    )
            }

            files.forEach { file ->
                    items.add(
                        FileItemData(
                            id = file.id,
                            name = file.displayName,
                            date = com.example.zerogram.util.FormatUtils.formatDate(file.createdAt),
                            size = com.example.zerogram.util.FormatUtils.formatSize(file.sizeBytes),
                            iconRes = getIconForMimeType(file.mimeType),
                            isFolder = false,
                            timestamp = file.createdAt,
                            sizeBytes = file.sizeBytes
                        )
                    )
            }
            
            var result = items.filter { it.name.contains(query, ignoreCase = true) }
            
            result = when (sort) {
                com.example.zerogram.ui.search.SortOrder.NEWEST_FIRST -> result.sortedByDescending { it.timestamp }
                com.example.zerogram.ui.search.SortOrder.NAME_A_Z -> result.sortedBy { it.name.lowercase() }
                com.example.zerogram.ui.search.SortOrder.NAME_Z_A -> result.sortedByDescending { it.name.lowercase() }
                com.example.zerogram.ui.search.SortOrder.LARGEST_FIRST -> result.sortedWith(compareByDescending<FileItemData> { !it.isFolder }.thenByDescending { it.sizeBytes })
                com.example.zerogram.ui.search.SortOrder.SMALLEST_FIRST -> result.sortedWith(compareByDescending<FileItemData> { !it.isFolder }.thenBy { it.sizeBytes })
            }
            
            result
        }
    }.flowOn(Dispatchers.Default).distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: com.example.zerogram.ui.search.SortOrder) {
        _sortOrder.value = order
    }



    private fun getIconForMimeType(mimeType: String): Int {
        return when {
            mimeType.startsWith("image/") -> R.drawable.ic_category_pic
            mimeType.startsWith("video/") -> R.drawable.ic_category_video
            mimeType.startsWith("audio/") -> R.drawable.ic_category_audio
            mimeType == "application/pdf" -> R.drawable.ic_category_doc
            mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("tar") -> R.drawable.ic_category_archive
            mimeType.contains("android.package-archive") -> R.drawable.ic_category_apk
            else -> R.drawable.ic_category_doc
        }
    }

    fun setFolderId(folderId: String?) {
        _currentFolderId.value = folderId
    }

    fun handleFileSelection(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val vaultKey = vaultManager.getActiveKey()
                if (vaultKey == null) {
                    _uiEvents.emit("Vault is locked. Unlock vault before uploading.")
                    return@withContext
                }

                val realPath = com.example.zerogram.util.UriUtils.getPath(context, uri)
                if (realPath == null) {
                    _uiEvents.emit("Could not resolve file path.")
                    return@withContext
                }

                var displayName = "Unknown_File"
                var sizeBytes = 0L
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                // Extract name and size
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) displayName = cursor.getString(nameIndex)

                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }

                if (sizeBytes > 2L * 1024 * 1024 * 1024) {
                    _uiEvents.emit("Unable to upload: File exceeds 2GB limit.")
                    return@withContext
                }

                // Generate UUIDs
                val fileId = UUID.randomUUID().toString()
                val jobId = UUID.randomUUID().toString()

                // Insert into DB without encrypting up front. Encryption happens on-demand when upload starts.
                val now = System.currentTimeMillis()
                val fileEntity = FileEntity(
                    id = fileId,
                    folderId = _currentFolderId.value,
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    encryptedSizeBytes = null,
                    baseIv = "", // We can add IV logic later if needed
                    checksum = null,
                    telegramMessageId = null,
                    telegramFileId = null,
                    uploadStatus = "pending",
                    createdAt = now,
                    updatedAt = now
                )

                val jobEntity = TransferJobEntity(
                    id = jobId,
                    fileId = fileId,
                    type = "upload",
                    status = "queued",
                    progressBytes = 0L,
                    totalBytes = sizeBytes,
                    retryCount = 0,
                    lastError = null,
                    sourcePath = realPath,
                    createdAt = now,
                    updatedAt = now
                )

                database.fileDao().insertFile(fileEntity)
                database.transferJobDao().insertJob(jobEntity)
                TransferService.startService(context)
            }
        }
    }

    fun handleNativeFileSelection(file: File) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val vaultKey = vaultManager.getActiveKey()
                if (vaultKey == null) {
                    _uiEvents.emit("Vault is locked. Unlock vault before uploading.")
                    return@withContext
                }

                if (!file.exists()) {
                    _uiEvents.emit("File does not exist.")
                    return@withContext
                }

                val displayName = file.name
                val sizeBytes = file.length()
                val extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(file.absolutePath)
                val mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension?.lowercase()) ?: "application/octet-stream"

                if (sizeBytes > 2L * 1024 * 1024 * 1024) {
                    _uiEvents.emit("Unable to upload: File exceeds 2GB limit.")
                    return@withContext
                }

                val fileId = UUID.randomUUID().toString()
                val jobId = UUID.randomUUID().toString()

                val now = System.currentTimeMillis()
                val fileEntity = FileEntity(
                    id = fileId,
                    folderId = _currentFolderId.value,
                    displayName = displayName,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes,
                    encryptedSizeBytes = null,
                    baseIv = "",
                    checksum = null,
                    telegramMessageId = null,
                    telegramFileId = null,
                    uploadStatus = "pending",
                    createdAt = now,
                    updatedAt = now
                )

                val jobEntity = TransferJobEntity(
                    id = jobId,
                    fileId = fileId,
                    type = "upload",
                    status = "queued",
                    progressBytes = 0L,
                    totalBytes = sizeBytes,
                    retryCount = 0,
                    lastError = null,
                    sourcePath = file.absolutePath,
                    createdAt = now,
                    updatedAt = now
                )

                database.fileDao().insertFile(fileEntity)
                database.transferJobDao().insertJob(jobEntity)
                TransferService.startService(context)
            }
        }
    }

    fun onFileClicked(fileId: String) {
        viewModelScope.launch {
            val fileEntity = database.fileDao().getFileById(fileId) ?: return@launch
            val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Zerogram")
            val targetFile = File(downloadsDir, fileEntity.displayName)
            
            if (targetFile.exists() && targetFile.length() > 0) {
                // Open file
                com.example.zerogram.core.utils.FileOpener.openFile(context, targetFile, fileEntity.mimeType)
            } else {
                // Check if download job exists
                val existingDownloadJob = database.transferJobDao().getJobByFileIdAndType(fileId, "download")
                
                if (existingDownloadJob == null) {
                    // Start download
                    val jobId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val jobEntity = TransferJobEntity(
                        id = jobId,
                        fileId = fileId,
                        type = "download",
                        status = "queued",
                        progressBytes = 0L,
                        totalBytes = fileEntity.sizeBytes,
                        retryCount = 0,
                        lastError = null,
                        createdAt = now,
                        updatedAt = now
                    )
                    database.transferJobDao().insertJob(jobEntity)
                    
                    // Wake up TransferService
                    com.example.zerogram.service.TransferService.startService(context)
                    
                    _uiEvents.emit("Download started for ${fileEntity.displayName}")
                } else if (existingDownloadJob.status == "completed") {
                    _uiEvents.emit("File already downloaded. Check your Downloads folder.")
                } else {
                    _uiEvents.emit("Already downloading ${fileEntity.displayName}")
                }
            }
        }
    }

    fun navigateToFolder(folderId: String, folderName: String) {
        val currentBreadcrumbs = _breadcrumbs.value.toMutableList()
        currentBreadcrumbs.add(Breadcrumb(folderId, folderName))
        _breadcrumbs.value = currentBreadcrumbs
        _currentFolderId.value = folderId
    }

    fun jumpToFolder(folderId: String?) {
        val targetFolderId = folderId?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            var currentId: String? = targetFolderId
            val pathParts = mutableListOf<Breadcrumb>()
            while (currentId != null) {
                val folder = database.folderDao().getFolderById(currentId)
                if (folder != null) {
                    pathParts.add(0, Breadcrumb(folder.id, folder.name))
                    currentId = folder.parentId
                } else {
                    break
                }
            }
            pathParts.add(0, Breadcrumb(null, "All files"))
            _breadcrumbs.value = pathParts
            _currentFolderId.value = targetFolderId
        }
    }

    fun navigateBack(): Boolean {
        val currentBreadcrumbs = _breadcrumbs.value.toMutableList()
        if (currentBreadcrumbs.size > 1) {
            currentBreadcrumbs.removeAt(currentBreadcrumbs.lastIndex)
            _breadcrumbs.value = currentBreadcrumbs
            _currentFolderId.value = currentBreadcrumbs.last().id
            _selectedItems.value = emptySet()
            return true
        }
        return false
    }

    fun toggleSelection(itemId: String) {
        _selectedItems.update { current ->
            if (current.contains(itemId)) current - itemId else current + itemId
        }
    }

    fun selectAll(itemIds: List<String>) {
        _selectedItems.value = itemIds.toSet()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    fun renameItem(itemId: String, newName: String) {
        viewModelScope.launch {
            val file = database.fileDao().getFileById(itemId)
            if (file != null) {
                database.fileDao().updateFile(file.copy(displayName = newName, updatedAt = System.currentTimeMillis()))
                return@launch
            }
            val folder = database.folderDao().getFolderById(itemId)
            if (folder != null) {
                database.folderDao().updateFolder(folder.copy(name = newName, updatedAt = System.currentTimeMillis()))
                return@launch
            }
        }
    }

    fun performAction(action: String, targetFolderId: String?) {
        val selectedIds = _selectedItems.value.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            try {
                if (action == "move") {
                    // Check for invalid move (moving a folder into itself or its descendants is complex, but basic check:)
                    if (targetFolderId in selectedIds) {
                        _uiEvents.emit("Cannot move a folder into itself.")
                        return@launch
                    }
                    database.fileDao().moveFiles(selectedIds, targetFolderId)
                    database.folderDao().moveFolders(selectedIds, targetFolderId)
                    _uiEvents.emit("Moved ${selectedIds.size} items successfully")
                } else if (action == "copy") {
                    if (targetFolderId in selectedIds) {
                        _uiEvents.emit("Cannot copy a folder into itself.")
                        return@launch
                    }
                    var count = 0
                    for (id in selectedIds) {
                        val file = database.fileDao().getFileById(id)
                        if (file != null) {
                            database.fileDao().insertFile(file.copy(
                                id = UUID.randomUUID().toString(),
                                folderId = targetFolderId,
                                createdAt = System.currentTimeMillis(),
                                updatedAt = System.currentTimeMillis()
                            ))
                            count++
                        } else {
                            val folder = database.folderDao().getFolderById(id)
                            if (folder != null) {
                                count += copyFolderRecursive(folder, targetFolderId)
                            }
                        }
                    }
                    _uiEvents.emit("Copied $count items successfully")
                }
            } catch (e: Exception) {
                _uiEvents.emit("Action failed: ${e.message}")
            } finally {
                clearSelection()
            }
        }
    }

    private suspend fun copyFolderRecursive(folder: com.example.zerogram.data.local.entity.FolderEntity, targetParentId: String?): Int {
        var count = 1
        val newFolderId = UUID.randomUUID().toString()
        val newFolder = folder.copy(
            id = newFolderId,
            parentId = targetParentId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        database.folderDao().insertFolder(newFolder)

        val childFiles = database.fileDao().getFilesByFolderIdOnce(folder.id)
        for (f in childFiles) {
            database.fileDao().insertFile(f.copy(
                id = UUID.randomUUID().toString(),
                folderId = newFolderId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            ))
            count++
        }

        val childFolders = database.folderDao().getFoldersByParentIdOnce(folder.id)
        for (f in childFolders) {
            count += copyFolderRecursive(f, newFolderId)
        }
        return count
    }

    fun createNewFolder(folderName: String) {
        viewModelScope.launch {
            val folderId = UUID.randomUUID().toString()
            val parentId = _currentFolderId.value
            val folderEntity = com.example.zerogram.data.local.entity.FolderEntity(
                id = folderId,
                name = folderName,
                parentId = parentId,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            database.folderDao().insertFolder(folderEntity)
            _uiEvents.emit("Folder '$folderName' created")
        }
    }

    fun moveToTrash() {
        val selectedIds = _selectedItems.value.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                database.fileDao().moveFilesToTrash(selectedIds, timestamp)
                database.folderDao().moveFoldersToTrash(selectedIds, timestamp)
                _selectedItems.value = emptySet()
                _uiEvents.emit("Moved ${selectedIds.size} items to Recently Deleted")
            } catch (e: Exception) {
                _uiEvents.emit("Error moving to trash: ${e.message}")
            }
        }
    }

    fun getSelectionDetails(onResult: (SelectionDetails) -> Unit) {
        viewModelScope.launch {
            val selectedIds = _selectedItems.value.toList()
            if (selectedIds.isEmpty()) return@launch
            
            val dateFormat = SimpleDateFormat("d MMMM yyyy h:mm a", Locale.getDefault())
            val currentItems = filesAndFolders.value
            val selectedFilesAndFolders = currentItems.filter { it.id in selectedIds }
            val locationStr = "All files" + breadcrumbs.value.drop(1).joinToString("") { "/${it.name}" }
            
            if (selectedIds.size == 1) {
                val item = selectedFilesAndFolders.first()
                if (item.isFolder) {
                    val folderEntity = database.folderDao().getFolderById(item.id)
                    val stats = database.folderDao().getFolderSubtreeStats(item.id)
                    val itemsText = buildString {
                        val totalFolders = stats.totalFolders + 1
                        val totalFiles = stats.totalFiles
                        append("$totalFolders folder${if(totalFolders > 1) "s" else ""}")
                        if (totalFiles > 0) append(", $totalFiles file${if(totalFiles > 1) "s" else ""}")
                    }
                    val formattedDate = folderEntity?.let { dateFormat.format(Date(it.createdAt)).lowercase(Locale.getDefault()) } ?: item.date
                    
                    onResult(
                        SelectionDetails(
                            title = "Details",
                            isMultiple = false,
                            name = item.name,
                            dateModified = formattedDate,
                            sizeText = com.example.zerogram.util.FormatUtils.formatSize(stats.totalSize),
                            location = locationStr,
                            itemsText = itemsText
                        )
                    )
                } else {
                    val fileEntity = database.fileDao().getFileById(item.id)
                    val formattedDate = fileEntity?.let { dateFormat.format(Date(it.createdAt)).lowercase(Locale.getDefault()) } ?: item.date
                    val formattedSize = fileEntity?.sizeBytes?.let { com.example.zerogram.util.FormatUtils.formatSize(it) } ?: item.size
                    onResult(
                        SelectionDetails(
                            title = "Details",
                            isMultiple = false,
                            name = item.name,
                            dateModified = formattedDate,
                            sizeText = formattedSize,
                            location = locationStr,
                            itemsText = null
                        )
                    )
                }
            } else {
                var totalSize = 0L
                var totalFolders = 0
                var totalFiles = 0
                
                val folderIds = selectedFilesAndFolders.filter { it.isFolder }.map { it.id }
                if (folderIds.isNotEmpty()) {
                    totalFolders += folderIds.size
                    val stats = database.folderDao().getAggregateFolderSubtreeStats(folderIds)
                    totalSize += stats.totalSize
                    totalFolders += stats.totalFolders
                    totalFiles += stats.totalFiles
                }
                
                for (item in selectedFilesAndFolders) {
                    if (!item.isFolder) {
                        totalFiles++
                        totalSize += item.sizeBytes
                    }
                }
                
                val itemsText = buildString {
                    if (totalFolders > 0) append("$totalFolders folder${if(totalFolders > 1) "s" else ""}")
                    if (totalFolders > 0 && totalFiles > 0) append(", ")
                    if (totalFiles > 0) append("$totalFiles file${if(totalFiles > 1) "s" else ""}")
                }.ifEmpty { "0 items" }
                
                onResult(
                    SelectionDetails(
                        title = "Details",
                        isMultiple = true,
                        sizeText = com.example.zerogram.util.FormatUtils.formatSize(totalSize),
                        itemsText = itemsText
                    )
                )
            }
        }
    }
}
