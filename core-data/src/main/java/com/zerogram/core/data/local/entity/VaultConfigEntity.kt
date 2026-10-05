package com.zerogram.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_config")
data class VaultConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    
    @ColumnInfo(name = "telegram_user_id")
    val telegramUserId: Long,
    
    @ColumnInfo(name = "storage_chat_id")
    val storageChatId: Long?,
    
    @ColumnInfo(name = "key_version", defaultValue = "1")
    val keyVersion: Int,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "vault_name", defaultValue = "My Vault")
    val vaultName: String = "My Vault"
)
