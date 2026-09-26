package com.zerogram.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zerogram.data.local.dao.FileDao
import com.zerogram.data.local.dao.FolderDao
import com.zerogram.data.local.dao.TransferJobDao
import com.zerogram.data.local.dao.VaultConfigDao
import com.zerogram.data.local.entity.FileEntity
import com.zerogram.data.local.entity.FolderEntity
import com.zerogram.data.local.entity.TransferJobEntity
import com.zerogram.data.local.entity.VaultConfigEntity

@Database(
    entities = [
        FolderEntity::class,
        FileEntity::class,
        TransferJobEntity::class,
        VaultConfigEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ZerogramDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun transferJobDao(): TransferJobDao
    abstract fun vaultConfigDao(): VaultConfigDao

    companion object {
        const val DATABASE_NAME = "zerogram_vault.db"
    }
}
