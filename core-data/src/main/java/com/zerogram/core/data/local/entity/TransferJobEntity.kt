package com.zerogram.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transfer_jobs",
    foreignKeys = [
        ForeignKey(
            entity = FileEntity::class,
            parentColumns = ["id"],
            childColumns = ["file_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["file_id"]),
        Index(value = ["status"])
    ]
)
data class TransferJobEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "file_id")
    val fileId: String,
    
    @ColumnInfo(name = "type")
    val type: String, // 'upload' or 'download'
    
    @ColumnInfo(name = "status", defaultValue = "queued")
    val status: String,
    
    @ColumnInfo(name = "progress_bytes", defaultValue = "0")
    val progressBytes: Long,
    
    @ColumnInfo(name = "total_bytes")
    val totalBytes: Long,
    
    @ColumnInfo(name = "retry_count", defaultValue = "0")
    val retryCount: Int,
    
    @ColumnInfo(name = "last_error")
    val lastError: String?,
    
    @ColumnInfo(name = "source_path")
    val sourcePath: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
