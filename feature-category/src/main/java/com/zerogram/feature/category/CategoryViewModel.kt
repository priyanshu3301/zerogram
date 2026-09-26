package com.example.zerogram.ui.category

import android.content.Context
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerogram.data.local.ZerogramDatabase
import com.example.zerogram.data.local.entity.TransferJobEntity
import com.example.zerogram.ui.folder.FileItemData
import com.example.zerogram.ui.folder.SelectionDetails
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.io.File
import javax.inject.Inject
import com.example.zerogram.R
import com.example.zerogram.core.utils.FileOpener
import com.example.zerogram.service.TransferService
import com.example.zerogram.ui.search.SortOrder
import com.example.zerogram.util.FormatUtils
import kotlinx.coroutines.Dispatchers

@HiltViewModel
class CategoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: ZerogramDatabase
) : ViewModel() {

    private val _categoryName = MutableStateFlow<String>("")

    private val _selectedItems = MutableStateFlow<Set<String>>(emptySet())
    val selectedItems: StateFlow<Set<String>> = _selectedItems.asStateFlow()

    private val _uiEvents = MutableSharedFlow<String>()
    val uiEvents = _uiEvents.asSharedFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    fun setCategory(category: String) {
        _categoryName.value = category
    }

    val filesAndFolders: StateFlow<List<FileItemData>> = combine(
        _categoryName.flatMapLatest { category -> database.fileDao().getFilesByCategory(category) },
        _searchQuery,
        _sortOrder
    ) { files, query, sort ->
        val items = mutableListOf<FileItemData>()
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        files.forEach { file ->
            items.add(
                FileItemData(
                    id = file.id,
                    name = file.displayName,
                    date = dateFormat.format(Date(file.createdAt)),
                    size = FormatUtils.formatSize(file.sizeBytes),
                    iconRes = getIconForMimeType(file.mimeType),
                    isFolder = false,
                    timestamp = file.createdAt,
                    sizeBytes = file.sizeBytes
                )
            )
        }
        
        var result = items.filter { it.name.contains(query, ignoreCase = true) }
        
        result = when (sort) {
            SortOrder.NEWEST_FIRST -> result.sortedByDescending { it.timestamp }
            SortOrder.NAME_A_Z -> result.sortedBy { it.name.lowercase() }
            SortOrder.NAME_Z_A -> result.sortedByDescending { it.name.lowercase() }
            SortOrder.LARGEST_FIRST -> result.sortedByDescending { it.sizeBytes }
            SortOrder.SMALLEST_FIRST -> result.sortedBy { it.sizeBytes }
        }
        
        result
    }.flowOn(Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
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
            }
        }
    }

    fun moveToTrash() {
        val selectedIds = _selectedItems.value.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                database.fileDao().moveFilesToTrash(selectedIds, timestamp)
                _selectedItems.value = emptySet()
                _uiEvents.emit("Moved ${selectedIds.size} items to Recently Deleted")
            } catch (e: Exception) {
                _uiEvents.emit("Error moving to trash: ${e.message}")
            }
        }
    }

    fun performAction(action: String, targetFolderId: String?) {
        val selectedIds = _selectedItems.value.toList()
        if (selectedIds.isEmpty()) return

        viewModelScope.launch {
            try {
                if (action == "move") {
                    database.fileDao().moveFiles(selectedIds, targetFolderId)
                    _uiEvents.emit("Moved ${selectedIds.size} items successfully")
                } else if (action == "copy") {
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

    fun onFileClicked(fileId: String) {
        viewModelScope.launch {
            val fileEntity = database.fileDao().getFileById(fileId) ?: return@launch
            val downloadsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Zerogram")
            val targetFile = File(downloadsDir, fileEntity.displayName)
            
            if (targetFile.exists() && targetFile.length() > 0) {
                FileOpener.openFile(context, targetFile, fileEntity.mimeType)
            } else {
                val existingDownloadJob = database.transferJobDao().getJobByFileIdAndType(fileId, "download")
                if (existingDownloadJob == null) {
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
                    TransferService.startService(context)
                    _uiEvents.emit("Download started for ${fileEntity.displayName}")
                } else {
                    _uiEvents.emit("Already downloading ${fileEntity.displayName}")
                }
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
            
            if (selectedIds.size == 1) {
                val item = selectedFilesAndFolders.first()
                val fileEntity = database.fileDao().getFileById(item.id)
                val formattedDate = fileEntity?.let { dateFormat.format(Date(it.createdAt)).lowercase(Locale.getDefault()) } ?: item.date
                val formattedSize = fileEntity?.sizeBytes?.let { FormatUtils.formatSize(it) } ?: item.size
                
                // For categories, we might not know the exact path easily, just show category
                val locationStr = "Category: ${_categoryName.value}"
                
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
            } else {
                var totalSize = 0L
                var totalFiles = 0
                
                for (item in selectedFilesAndFolders) {
                    totalFiles++
                    val fileEntity = database.fileDao().getFileById(item.id)
                    if (fileEntity != null) {
                        totalSize += fileEntity.sizeBytes
                    }
                }
                
                val itemsText = "$totalFiles file${if(totalFiles > 1) "s" else ""}"
                
                onResult(
                    SelectionDetails(
                        title = "Details",
                        isMultiple = true,
                        sizeText = FormatUtils.formatSize(totalSize),
                        itemsText = itemsText
                    )
                )
            }
        }
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
}
