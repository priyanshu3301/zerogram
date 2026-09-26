package com.zerogram.feature.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerogram.data.local.ZerogramDatabase
import com.zerogram.core.ui.components.AppListItem
import com.zerogram.core.ui.components.SelectionDetails
import com.zerogram.core.ui.components.SortOrder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentlyDeletedViewModel @Inject constructor(
    private val database: ZerogramDatabase
) : ViewModel() {

    private val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

    private val _searchQuery = kotlinx.coroutines.flow.MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortOrder = kotlinx.coroutines.flow.MutableStateFlow(SortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    val deletedItems: StateFlow<List<AppListItem>> = combine(
        database.folderDao().getDeletedFoldersWithCount(),
        database.fileDao().getDeletedFiles(),
        _searchQuery,
        _sortOrder
    ) { folders, files, query, sort ->
        val currentTime = System.currentTimeMillis()
        val items = mutableListOf<AppListItem>()

        val foldersToDelete = mutableListOf<String>()
        val filesToDelete = mutableListOf<String>()
        val dateFormat = java.text.SimpleDateFormat("d MMMM", java.util.Locale.getDefault())

        folders.forEach { folder ->
            val deletedAt = folder.deletedAt ?: 0L
            if (currentTime - deletedAt > THIRTY_DAYS_MS) {
                foldersToDelete.add(folder.id)
            } else {
                val sizeText = if (folder.itemCount == 1) "1 item" else "${folder.itemCount} items"
                items.add(
                    AppListItem.Folder(
                        id = folder.id,
                        name = folder.name,
                        dateText = dateFormat.format(java.util.Date(folder.createdAt)),
                        extraInfo = sizeText,
                        iconRes = com.zerogram.core.ui.R.drawable.ic_file_folder_icon
                    )
                )
            }
        }

        files.forEach { file ->
            val deletedAt = file.deletedAt ?: 0L
            if (currentTime - deletedAt > THIRTY_DAYS_MS) {
                filesToDelete.add(file.id)
            } else {
                items.add(
                    AppListItem.File(
                        id = file.id,
                        name = file.displayName,
                        dateText = dateFormat.format(java.util.Date(file.createdAt)),
                        sizeText = com.zerogram.util.FormatUtils.formatSize(file.sizeBytes),
                        iconRes = getIconForMimeType(file.mimeType),
                        sizeBytes = file.sizeBytes
                    )
                )
            }
        }

        var result = items.filter { (if (it is AppListItem.Folder) it.name else (it as AppListItem.File).name).contains(query, ignoreCase = true) }
        
        result = when (sort) {
            SortOrder.NEWEST_FIRST -> result
            SortOrder.NAME_A_Z -> result.sortedBy { (if (it is AppListItem.Folder) it.name else (it as AppListItem.File).name).lowercase() }
            SortOrder.NAME_Z_A -> result.sortedByDescending { (if (it is AppListItem.Folder) it.name else (it as AppListItem.File).name).lowercase() }
            SortOrder.LARGEST_FIRST -> result.sortedWith(compareByDescending<AppListItem> { it is AppListItem.File }.thenByDescending { if (it is AppListItem.File) it.sizeBytes else 0L })
            SortOrder.SMALLEST_FIRST -> result.sortedWith(compareByDescending<AppListItem> { it is AppListItem.File }.thenBy { if (it is AppListItem.File) it.sizeBytes else 0L })
        }
        
        result
    }.flowOn(Dispatchers.Default).distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    private val _selectedItems = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems

    fun toggleSelection(id: String) {
        val current = _selectedItems.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedItems.value = current
    }

    fun selectAll() {
        _selectedItems.value = deletedItems.value.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedItems.value = emptySet()
    }

    fun recoverSelected() {
        val ids = _selectedItems.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val foldersToRecover = deletedItems.value.filter { it is AppListItem.Folder && it.id in ids }.map { it.id }
            val filesToRecover = deletedItems.value.filter { it is AppListItem.File && it.id in ids }.map { it.id }

            if (foldersToRecover.isNotEmpty()) database.folderDao().recoverFolders(foldersToRecover)
            if (filesToRecover.isNotEmpty()) database.fileDao().recoverFiles(filesToRecover)

            clearSelection()
        }
    }

    fun deleteSelectedPermanently() {
        val ids = _selectedItems.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val foldersToDelete = deletedItems.value.filter { it is AppListItem.Folder && it.id in ids }.map { it.id }
            val filesToDelete = deletedItems.value.filter { it is AppListItem.File && it.id in ids }.map { it.id }

            if (foldersToDelete.isNotEmpty()) database.folderDao().deleteFoldersPermanently(foldersToDelete)
            if (filesToDelete.isNotEmpty()) database.fileDao().deleteFilesPermanently(filesToDelete)

            clearSelection()
        }
    }

    fun getIconForMimeType(mimeType: String): Int {
        return when {
            mimeType.startsWith("image/") -> com.zerogram.core.ui.R.drawable.ic_category_pic
            mimeType.startsWith("video/") -> com.zerogram.core.ui.R.drawable.ic_category_video
            mimeType.startsWith("audio/") -> com.zerogram.core.ui.R.drawable.ic_category_audio
            mimeType.contains("pdf") || mimeType.contains("document") -> com.zerogram.core.ui.R.drawable.ic_category_doc
            mimeType.contains("zip") || mimeType.contains("rar") -> com.zerogram.core.ui.R.drawable.ic_category_archive
            mimeType.contains("android.package-archive") -> com.zerogram.core.ui.R.drawable.ic_category_apk
            else -> com.zerogram.core.ui.R.drawable.ic_category_doc
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            val folders = database.folderDao().getDeletedFolders().first()
            val files = database.fileDao().getDeletedFiles().first()

            if (folders.isNotEmpty()) {
                database.folderDao().deleteFoldersPermanently(folders.map { it.id })
            }
            if (files.isNotEmpty()) {
                database.fileDao().deleteFilesPermanently(files.map { it.id })
            }
        }
    }

    fun getSelectionDetails(onResult: (SelectionDetails) -> Unit) {
        viewModelScope.launch {
            val selectedIds = _selectedItems.value.toList()
            if (selectedIds.isEmpty()) return@launch
            
            val dateFormat = java.text.SimpleDateFormat("d MMMM yyyy h:mm a", java.util.Locale.getDefault())
            val currentItems = deletedItems.value
            val selectedFilesAndFolders = currentItems.filter { it.id in selectedIds }
            val locationStr = "Recently Deleted"
            
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
                    val formattedDate = folderEntity?.let { dateFormat.format(java.util.Date(it.createdAt)).lowercase(java.util.Locale.getDefault()) } ?: item.dateText
                    
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
                    val formattedDate = fileEntity?.let { dateFormat.format(java.util.Date(it.createdAt)).lowercase(java.util.Locale.getDefault()) } ?: item.dateText
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
