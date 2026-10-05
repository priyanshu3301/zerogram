package com.zerogram.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
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
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 5, to = 6, spec = ZerogramDatabase.Migration5To6::class)
    ]
)
abstract class ZerogramDatabase : RoomDatabase() {
    abstract fun folderDao(): FolderDao
    abstract fun fileDao(): FileDao
    abstract fun transferJobDao(): TransferJobDao
    abstract fun vaultConfigDao(): VaultConfigDao

    @DeleteColumn(tableName = "files", columnName = "base_iv")
    class Migration5To6 : AutoMigrationSpec

    companion object {
        const val DATABASE_NAME = "zerogram_vault.db"
    }
}
