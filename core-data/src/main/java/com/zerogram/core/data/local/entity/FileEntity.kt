package com.zerogram.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "files",
    foreignKeys = [
        ForeignKey(
            entity = FolderEntity::class,
            parentColumns = ["id"],
            childColumns = ["folder_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["display_name"])
    ]
)
data class FileEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "folder_id")
    val folderId: String?,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "mime_type")
    val mimeType: String,
    
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    
    @ColumnInfo(name = "encrypted_size_bytes")
    val encryptedSizeBytes: Long?,
    
    @ColumnInfo(name = "base_iv")
    val baseIv: String,
    
    @ColumnInfo(name = "checksum")
    val checksum: String?,
    
    @ColumnInfo(name = "telegram_message_id")
    val telegramMessageId: Long?,
    
    @ColumnInfo(name = "telegram_file_id")
    val telegramFileId: String?,
    
    @ColumnInfo(name = "upload_status", defaultValue = "pending")
    val uploadStatus: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null
)
