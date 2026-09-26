package com.zerogram.feature.folder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerogram.data.local.dao.FolderDao
import com.zerogram.data.local.dao.FolderWithCount
import com.zerogram.data.local.entity.FolderEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val folderDao: FolderDao
) : ViewModel() {

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _breadcrumbs = MutableStateFlow<List<Breadcrumb>>(listOf(Breadcrumb(null, "All files")))
    val breadcrumbs: StateFlow<List<Breadcrumb>> = _breadcrumbs.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val folders: StateFlow<List<FolderWithCount>> = _currentFolderId
        .flatMapLatest { folderId ->
            folderDao.getFoldersWithCountByParentId(folderId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun navigateToFolder(folderId: String, folderName: String) {
        _currentFolderId.value = folderId
        _breadcrumbs.update { current ->
            current + Breadcrumb(folderId, folderName)
        }
    }

    fun navigateBack(): Boolean {
        return if (_breadcrumbs.value.size > 1) {
            val newBreadcrumbs = _breadcrumbs.value.dropLast(1)
            _breadcrumbs.value = newBreadcrumbs
            _currentFolderId.value = newBreadcrumbs.last().id
            true
        } else {
            false
        }
    }

    fun createNewFolder(name: String) {
        viewModelScope.launch {
            val newFolder = FolderEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                parentId = _currentFolderId.value,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            folderDao.insertFolder(newFolder)
        }
    }
}
