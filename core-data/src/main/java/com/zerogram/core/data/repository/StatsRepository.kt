package com.example.zerogram.data.repository

import com.example.zerogram.data.local.ZerogramDatabase
import com.example.zerogram.data.local.dao.HomeStats
import com.example.zerogram.util.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepository @Inject constructor(
    private val database: ZerogramDatabase
) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val vaultName: StateFlow<String> = database.vaultConfigDao().getVaultConfigFlow()
        .map { it?.vaultName ?: "My Files" }
        .catch { emit("My Files") }
        .stateIn(applicationScope, SharingStarted.WhileSubscribed(5000), "My Files")

    val homeStats: StateFlow<HomeStats> = database.fileDao().getHomeStats()
        .catch { emit(HomeStats(0, 0, 0, 0, 0, 0, 0L)) }
        .stateIn(applicationScope, SharingStarted.WhileSubscribed(5000), HomeStats(0, 0, 0, 0, 0, 0, 0L))

    val deletedStats: StateFlow<String> = combine(
        database.folderDao().getDeletedFoldersCount(),
        database.fileDao().getDeletedFilesStats()
    ) { folderCount, fileStats ->
        val totalItems = folderCount + fileStats.count
        if (totalItems == 0) {
            "0 items | 0 B"
        } else {
            "$totalItems items | ${FormatUtils.formatSize(fileStats.totalSize)}"
        }
    }
    .catch { emit("0 items | 0 B") }
    .stateIn(applicationScope, SharingStarted.WhileSubscribed(5000), "0 items | 0 B")
}
