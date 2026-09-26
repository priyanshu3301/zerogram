package com.example.zerogram.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerogram.R
import com.example.zerogram.data.local.ZerogramDatabase
import com.example.zerogram.ui.folder.FileItemData
import com.example.zerogram.ui.folder.SelectionDetails
import com.example.zerogram.util.FormatUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import javax.inject.Inject

enum class SortOrder {
    NEWEST_FIRST,
    NAME_A_Z,
    NAME_Z_A,
    LARGEST_FIRST,
    SMALLEST_FIRST
}

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ZerogramDatabase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    val filesAndFolders: StateFlow<List<FileItemData>> = combine(
        _searchQuery.debounce(250),
        _sortOrder
    ) { query, _ ->
        query
    }.flatMapLatest { query ->
        if (query.isBlank()) {
            flowOf(emptyList())
        } else {
            combine(
                database.folderDao().searchFoldersWithCount(query),
                database.fileDao().searchFiles(query)
            ) { folders, files ->
                val items = mutableListOf<FileItemData>()

                folders.forEach { folder ->
                    val itemsText = if (folder.itemCount == 1) "1 item" else "${folder.itemCount} items"
                    items.add(
                        FileItemData(
                            id = folder.id,
                            name = folder.name,
                            date = FormatUtils.formatDate(folder.createdAt),
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
                            date = FormatUtils.formatDate(file.createdAt),
                            size = FormatUtils.formatSize(file.sizeBytes),
                            iconRes = getIconForMimeType(file.mimeType),
                            isFolder = false,
                            timestamp = file.createdAt,
                            sizeBytes = file.sizeBytes
                        )
                    )
                }

                val currentSortOrder = _sortOrder.value
                when (currentSortOrder) {
                    SortOrder.NEWEST_FIRST -> items.sortedByDescending { it.timestamp }
                    SortOrder.NAME_A_Z -> items.sortedBy { it.name.lowercase() }
                    SortOrder.NAME_Z_A -> items.sortedByDescending { it.name.lowercase() }
                    SortOrder.LARGEST_FIRST -> items.sortedByDescending { it.sizeBytes }
                    SortOrder.SMALLEST_FIRST -> items.sortedBy { it.sizeBytes }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // UI Action methods duplicated from FolderViewModel
    fun toggleSelection(itemId: String) {
        _selectedItems.update { current ->
            if (current.contains(itemId)) current - itemId else current + itemId
        }
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
                _selectedItems.value = emptySet()
            } catch (e: Exception) {
                _uiEvents.emit("Error during $action: ${e.message}")
            }
        }
    }

    private suspend fun copyFolderRecursive(folder: com.example.zerogram.data.local.entity.FolderEntity, newParentId: String?): Int {
        var count = 1
        val newFolderId = UUID.randomUUID().toString()
        val newFolder = folder.copy(
            id = newFolderId,
            parentId = newParentId,
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
        }

        val childFolders = database.folderDao().getFoldersByParentIdOnce(folder.id)
        for (f in childFolders) {
            count += copyFolderRecursive(f, newFolderId)
        }
        return count
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

            val selectedFilesAndFolders = filesAndFolders.value.filter { it.id in selectedIds }

            if (selectedFilesAndFolders.size == 1) {
                val item = selectedFilesAndFolders.first()
                if (item.isFolder) {
                    val folderEntity = database.folderDao().getFolderById(item.id)
                    val stats = database.folderDao().getFolderSubtreeStats(item.id)
                    val itemsText = buildString {
                        val totalFolders = stats.totalFolders + 1
                        val totalFiles = stats.totalFiles
                        append("$totalFolders folder${if(totalFolders > 1) "s" else ""}")
                        append(", ")
                        append("$totalFiles file${if(totalFiles > 1) "s" else ""}")
                    }
                    onResult(
                        SelectionDetails(
                            title = "Details",
                            name = folderEntity?.name ?: item.name,
                            dateModified = folderEntity?.let { FormatUtils.formatDate(it.createdAt) } ?: item.date,
                            sizeText = FormatUtils.formatSize(stats.totalSize),
                            itemsText = itemsText,
                            location = getPathForFolder(folderEntity?.parentId),
                            isMultiple = false
                        )
                    )
                } else {
                    val fileEntity = database.fileDao().getFileById(item.id)
                    val formattedDate = fileEntity?.let { FormatUtils.formatDate(it.createdAt) } ?: item.date
                    val formattedSize = fileEntity?.sizeBytes?.let { FormatUtils.formatSize(it) } ?: item.size
                    onResult(
                        SelectionDetails(
                            title = "Details",
                            name = fileEntity?.displayName ?: item.name,
                            dateModified = formattedDate,
                            sizeText = formattedSize,
                            itemsText = null,
                            location = getPathForFolder(fileEntity?.folderId),
                            isMultiple = false
                        )
                    )
                }
            } else {
                var totalSize = 0L
                var totalFiles = 0
                var totalFolders = 0

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
                    append("$totalFolders folder${if(totalFolders > 1) "s" else ""}")
                    append(", ")
                    append("$totalFiles file${if(totalFiles > 1) "s" else ""}")
                }

                onResult(
                    SelectionDetails(
                        title = "Multiple Items",
                        name = "${selectedFilesAndFolders.size} items selected",
                        dateModified = "",
                        sizeText = FormatUtils.formatSize(totalSize),
                        itemsText = itemsText,
                        location = null,
                        isMultiple = true
                    )
                )
            }
        }
    }

    private suspend fun getPathForFolder(folderId: String?): String {
        if (folderId == null) return "Internal Storage"
        val path = mutableListOf<String>()
        var currentId: String? = folderId
        while (currentId != null) {
            val folder = database.folderDao().getFolderById(currentId)
            if (folder != null) {
                path.add(folder.name)
                currentId = folder.parentId
            } else {
                break
            }
        }
        path.add("Internal Storage")
        return path.reversed().joinToString(" > ")
    }

    fun onFileClicked(fileId: String) {
        viewModelScope.launch {
            val fileEntity = database.fileDao().getFileById(fileId) ?: return@launch
            val downloadsDir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "Zerogram")
            val targetFile = File(downloadsDir, fileEntity.displayName)
            
            if (targetFile.exists() && targetFile.length() > 0) {
                // Open file
            } else {
                val existingDownloadJob = database.transferJobDao().getJobByFileIdAndType(fileId, "download")
                if (existingDownloadJob == null) {
                    val jobId = UUID.randomUUID().toString()
                    val now = System.currentTimeMillis()
                    val jobEntity = com.example.zerogram.data.local.entity.TransferJobEntity(
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
                    com.example.zerogram.service.TransferService.startService(context)
                    _uiEvents.emit("Download started for ${fileEntity.displayName}")
                }
            }
        }
    }

    private fun getIconForMimeType(mimeType: String): Int {
        return when {
            mimeType.startsWith("image/") -> R.drawable.ic_category_pic
            mimeType.startsWith("video/") -> R.drawable.ic_category_video
            mimeType.startsWith("audio/") -> R.drawable.ic_category_audio
            mimeType.startsWith("application/vnd.android.package-archive") -> R.drawable.ic_category_apk
            mimeType.contains("zip") || mimeType.contains("rar") || mimeType.contains("tar") -> R.drawable.ic_category_archive
            else -> R.drawable.ic_category_doc
        }
    }
}
