package com.zerogram.feature.folder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerogram.data.local.ZerogramDatabase
import com.zerogram.data.local.entity.FileEntity
import com.zerogram.data.local.entity.TransferJobEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import com.zerogram.core.ui.components.AppListItem
import com.zerogram.core.ui.components.SortOrder
import com.zerogram.core.ui.components.SelectionDetails

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
import com.zerogram.core.ui.R
import com.zerogram.crypto.CryptoManager
import com.zerogram.domain.model.AppResult
import com.zerogram.domain.repository.IVaultManager

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    val filesAndFolders: StateFlow<List<AppListItem>> = _currentFolderId.flatMapLatest { folderId ->
        combine(
            database.folderDao().getFoldersWithCountByParentId(folderId),
            database.fileDao().getFilesByFolderId(folderId),
            _searchQuery,
            _sortOrder
        ) { folders, files, query, sort ->
            val items = mutableListOf<AppListItem>()

            folders.forEach { folder ->
                val itemsText = if (folder.itemCount == 1) "1 item" else "${folder.itemCount} items"
                    items.add(
                        AppListItem.Folder(
                            id = folder.id,
                            name = folder.name,
                            dateText = com.zerogram.util.FormatUtils.formatDate(folder.createdAt),
                            extraInfo = itemsText,
                            iconRes = R.drawable.ic_file_folder_icon,
                            timestamp = folder.createdAt
                        )
                    )
            }

            files.forEach { file ->
                    items.add(
                        AppListItem.File(
                            id = file.id,
                            name = file.displayName,
                            dateText = com.zerogram.util.FormatUtils.formatDate(file.createdAt),
                            sizeText = com.zerogram.util.FormatUtils.formatSize(file.sizeBytes),
                            iconRes = getIconForMimeType(file.mimeType),
                            timestamp = file.createdAt,
                            sizeBytes = file.sizeBytes
                        )
                    )
            }
            
            var result = items.filter { (if (it is AppListItem.Folder) it.name else (it as AppListItem.File).name).contains(query, ignoreCase = true) }
            
            result = when (sort) {
                SortOrder.NEWEST_FIRST -> result.sortedWith(compareBy<AppListItem> { it !is AppListItem.Folder }.thenByDescending { if (it is AppListItem.Folder) it.timestamp else (it as AppListItem.File).timestamp })
                SortOrder.NAME_A_Z -> result.sortedWith(compareBy<AppListItem> { it !is AppListItem.Folder }.thenBy { (if (it is AppListItem.Folder) it.name else (it as AppListItem.File).name).lowercase() })
                SortOrder.NAME_Z_A -> result.sortedWith(compareBy<AppListItem> { it !is AppListItem.Folder }.thenByDescending { (if (it is AppListItem.Folder) it.name else (it as AppListItem.File).name).lowercase() })
                SortOrder.LARGEST_FIRST -> result.sortedWith(compareBy<AppListItem> { it !is AppListItem.Folder }.thenByDescending { if (it is AppListItem.File) it.sizeBytes else 0L })
                SortOrder.SMALLEST_FIRST -> result.sortedWith(compareBy<AppListItem> { it !is AppListItem.Folder }.thenBy { if (it is AppListItem.File) it.sizeBytes else 0L })
            }
            
            result
        }
    }.flowOn(Dispatchers.Default).distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
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

                var realPath = com.zerogram.util.UriUtils.getPath(context, uri)
                if (realPath == null || !java.io.File(realPath).exists()) {
                    val cacheFile = com.zerogram.util.UriUtils.copyUriToCache(context, uri)
                    if (cacheFile != null) {
                        realPath = cacheFile.absolutePath
                    } else {
                        _uiEvents.emit("Could not resolve file path.")
                        return@withContext
                    }
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
                val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            context.startForegroundService(intent)
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
                com.zerogram.core.ui.utils.FileOpener.openFile(context, targetFile, fileEntity.mimeType)
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
                    val intent = android.content.Intent().apply { setClassName(context.packageName, "com.zerogram.service.TransferService") }
            context.startForegroundService(intent)
                    
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

    private suspend fun copyFolderRecursive(folder: com.zerogram.data.local.entity.FolderEntity, targetParentId: String?): Int {
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
            val folderEntity = com.zerogram.data.local.entity.FolderEntity(
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

                // Also mark files inside the trashed folders (and their descendants) as deleted
                if (selectedIds.isNotEmpty()) {
                    val allDescendantFolderIds = mutableListOf<String>()
                    allDescendantFolderIds.addAll(selectedIds)
                    // Recursively collect all descendant folder IDs
                    val queue = ArrayDeque<String>()
                    queue.addAll(selectedIds)
                    while (queue.isNotEmpty()) {
                        val parentId = queue.removeFirst()
                        val children = database.folderDao().getFoldersByParentIdOnce(parentId)
                        children.forEach { child ->
                            allDescendantFolderIds.add(child.id)
                            queue.add(child.id)
                        }
                    }
                    database.folderDao().moveFilesInFoldersToTrash(allDescendantFolderIds, timestamp)
                }

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
                if (item is AppListItem.Folder) {
                    val folderEntity = database.folderDao().getFolderById(item.id)
                    val stats = database.folderDao().getFolderSubtreeStats(item.id)
                    val itemsText = buildString {
                        val totalFolders = stats.totalFolders + 1
                        val totalFiles = stats.totalFiles
                        append("$totalFolders folder${if(totalFolders > 1) "s" else ""}")
                        if (totalFiles > 0) append(", $totalFiles file${if(totalFiles > 1) "s" else ""}")
                    }
                    val formattedDate = folderEntity?.let { dateFormat.format(Date(it.createdAt)).lowercase(Locale.getDefault()) } ?: item.dateText
                    
                    onResult(
                        SelectionDetails(
                            title = "Details",
                            isMultiple = false,
                            name = item.name,
                            dateModified = formattedDate,
                            sizeText = com.zerogram.util.FormatUtils.formatSize(stats.totalSize),
                            location = locationStr,
                            itemsText = itemsText
                        )
                    )
                } else if (item is AppListItem.File) {
                    val fileEntity = database.fileDao().getFileById(item.id)
                    val formattedDate = fileEntity?.let { dateFormat.format(Date(it.createdAt)).lowercase(Locale.getDefault()) } ?: item.dateText
                    val formattedSize = fileEntity?.sizeBytes?.let { com.zerogram.util.FormatUtils.formatSize(it) } ?: item.sizeText
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
                
                val folderIds = selectedFilesAndFolders.filterIsInstance<AppListItem.Folder>().map { it.id }
                if (folderIds.isNotEmpty()) {
                    totalFolders += folderIds.size
                    val stats = database.folderDao().getAggregateFolderSubtreeStats(folderIds)
                    totalSize += stats.totalSize
                    totalFolders += stats.totalFolders
                    totalFiles += stats.totalFiles
                }
                
                for (item in selectedFilesAndFolders) {
                    if (item is AppListItem.File) {
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
                        sizeText = com.zerogram.util.FormatUtils.formatSize(totalSize),
                        itemsText = itemsText
                    )
                )
            }
        }
    }
}
