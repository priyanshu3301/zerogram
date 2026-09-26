package com.example.zerogram.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zerogram.data.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.Immutable
import com.example.zerogram.data.local.dao.HomeStats
import com.example.zerogram.R
import com.example.zerogram.util.FormatUtils

@Immutable
data class CategoryStats(
    val title: String,
    val count: String,
    val iconRes: Int
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val statsRepository: StatsRepository
) : ViewModel() {

    val vaultName: StateFlow<String> = statsRepository.vaultName

    val homeStats: StateFlow<HomeStats> = statsRepository.homeStats

    val totalStorageBytes: StateFlow<Long> = homeStats
        .map { it.totalSize }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), homeStats.value.totalSize)

    val totalStorageString: StateFlow<String> = totalStorageBytes
        .map { FormatUtils.formatSize(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FormatUtils.formatSize(homeStats.value.totalSize))

    val categories: StateFlow<List<CategoryStats>> = homeStats
        .map { stats ->
            listOf(
                CategoryStats("Photos", stats.photos.toString(), R.drawable.ic_category_pic),
                CategoryStats("Videos", stats.videos.toString(), R.drawable.ic_category_video),
                CategoryStats("Audio", stats.audio.toString(), R.drawable.ic_category_audio),
                CategoryStats("Documents", stats.docs.toString(), R.drawable.ic_category_doc),
                CategoryStats("APKs", stats.apks.toString(), R.drawable.ic_category_apk),
                CategoryStats("Archives", stats.archives.toString(), R.drawable.ic_category_archive)
            )
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), createCategoryStats(homeStats.value))

    val deletedStats: StateFlow<String> = statsRepository.deletedStats

    private fun createCategoryStats(stats: HomeStats): List<CategoryStats> {
        return listOf(
            CategoryStats("Photos", stats.photos.toString(), R.drawable.ic_category_pic),
            CategoryStats("Videos", stats.videos.toString(), R.drawable.ic_category_video),
            CategoryStats("Audio", stats.audio.toString(), R.drawable.ic_category_audio),
            CategoryStats("Documents", stats.docs.toString(), R.drawable.ic_category_doc),
            CategoryStats("APKs", stats.apks.toString(), R.drawable.ic_category_apk),
            CategoryStats("Archives", stats.archives.toString(), R.drawable.ic_category_archive)
        )
    }
}
