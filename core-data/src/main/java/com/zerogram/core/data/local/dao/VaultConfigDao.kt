package com.zerogram.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zerogram.data.local.entity.VaultConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultConfigDao {
    @Query("SELECT * FROM vault_config WHERE id = 1")
    fun getVaultConfigFlow(): Flow<VaultConfigEntity?>

    @Query("SELECT * FROM vault_config WHERE id = 1")
    suspend fun getVaultConfig(): VaultConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateConfig(config: VaultConfigEntity)
}
