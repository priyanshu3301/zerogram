package com.zerogram.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zerogram.data.local.entity.FileEntity
import kotlinx.coroutines.flow.Flow

data class FileSummary(
    val id: String,
    @androidx.room.ColumnInfo(name = "displayName") val displayName: String,
    @androidx.room.ColumnInfo(name = "mimeType") val mimeType: String,
    @androidx.room.ColumnInfo(name = "sizeBytes") val sizeBytes: Long,
    @androidx.room.ColumnInfo(name = "createdAt") val createdAt: Long
)

data class HomeStats(
    val photos: Int,
    val videos: Int,
    val audio: Int,
    val apks: Int,
    val archives: Int,
    val docs: Int,
    @androidx.room.ColumnInfo(name = "totalSize") val totalSize: Long
)

data class DeletedStats(
    @ColumnInfo(name = "count") val count: Int,
    @ColumnInfo(name = "totalSize") val totalSize: Long
)

@Dao
interface FileDao {
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN mime_type LIKE 'image/%' THEN 1 ELSE 0 END), 0) AS photos,
            COALESCE(SUM(CASE WHEN mime_type LIKE 'video/%' THEN 1 ELSE 0 END), 0) AS videos,
            COALESCE(SUM(CASE WHEN mime_type LIKE 'audio/%' THEN 1 ELSE 0 END), 0) AS audio,
            COALESCE(SUM(CASE WHEN mime_type LIKE '%android.package-archive%' THEN 1 ELSE 0 END), 0) AS apks,
            COALESCE(SUM(CASE WHEN (mime_type NOT LIKE 'image/%' AND mime_type NOT LIKE 'video/%' AND mime_type NOT LIKE 'audio/%' AND mime_type NOT LIKE '%android.package-archive%' AND (mime_type LIKE '%zip%' OR mime_type LIKE '%rar%' OR mime_type LIKE '%tar%')) THEN 1 ELSE 0 END), 0) AS archives,
            COALESCE(SUM(CASE WHEN (mime_type NOT LIKE 'image/%' AND mime_type NOT LIKE 'video/%' AND mime_type NOT LIKE 'audio/%' AND mime_type NOT LIKE '%android.package-archive%' AND mime_type NOT LIKE '%zip%' AND mime_type NOT LIKE '%rar%' AND mime_type NOT LIKE '%tar%') THEN 1 ELSE 0 END), 0) AS docs,
            COALESCE(SUM(size_bytes), 0) AS totalSize
        FROM files WHERE upload_status = 'completed' AND deleted_at IS NULL
    """)
    fun getHomeStats(): Flow<HomeStats>

    @Query("""
        SELECT id, display_name AS displayName, mime_type AS mimeType, size_bytes AS sizeBytes, created_at AS createdAt
        FROM files 
        WHERE upload_status = 'completed' AND (
            (:category = 'Photos' AND mime_type LIKE 'image/%') OR
            (:category = 'Videos' AND mime_type LIKE 'video/%') OR
            (:category = 'Audio' AND mime_type LIKE 'audio/%') OR
            (:category = 'APKs' AND mime_type LIKE '%android.package-archive%') OR
            (:category = 'Archives' AND (mime_type LIKE '%zip%' OR mime_type LIKE '%rar%' OR mime_type LIKE '%tar%')) OR
            (:category = 'Documents' AND (mime_type NOT LIKE 'image/%' AND mime_type NOT LIKE 'video/%' AND mime_type NOT LIKE 'audio/%' AND mime_type NOT LIKE '%android.package-archive%' AND mime_type NOT LIKE '%zip%' AND mime_type NOT LIKE '%rar%' AND mime_type NOT LIKE '%tar%'))
        ) AND deleted_at IS NULL
    """)
    fun getFilesByCategory(category: String): Flow<List<FileSummary>>

    @Query("SELECT * FROM files WHERE ((:folderId IS NULL AND folder_id IS NULL) OR (folder_id = :folderId)) AND upload_status = 'completed' AND deleted_at IS NULL")
    fun getFilesByFolderId(folderId: String?): Flow<List<FileEntity>>

    @Query("SELECT * FROM files WHERE ((:folderId IS NULL AND folder_id IS NULL) OR (folder_id = :folderId)) AND upload_status = 'completed' AND deleted_at IS NULL")
    suspend fun getFilesByFolderIdOnce(folderId: String?): List<FileEntity>

    @Query("SELECT * FROM files WHERE id = :id")
    suspend fun getFileById(id: String): FileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: FileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFiles(files: List<FileEntity>)

    @Update
    suspend fun updateFile(file: FileEntity)

    @Query("DELETE FROM files WHERE id = :id")
    suspend fun deleteFile(id: String)

    @Query("UPDATE files SET folder_id = :targetFolderId WHERE id IN (:fileIds)")
    suspend fun moveFiles(fileIds: List<String>, targetFolderId: String?)

    @Query("UPDATE files SET deleted_at = :timestamp WHERE id IN (:fileIds)")
    suspend fun moveFilesToTrash(fileIds: List<String>, timestamp: Long)

    @Query("SELECT * FROM files WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    fun getDeletedFiles(): Flow<List<FileEntity>>

    @Query("SELECT COUNT(*) as count, COALESCE(SUM(size_bytes), 0) as totalSize FROM files WHERE deleted_at IS NOT NULL")
    fun getDeletedFilesStats(): Flow<DeletedStats>

    @Query("DELETE FROM files WHERE id IN (:fileIds)")
    suspend fun deleteFilesPermanently(fileIds: List<String>)

    @Query("UPDATE files SET deleted_at = NULL WHERE id IN (:fileIds)")
    suspend fun recoverFiles(fileIds: List<String>)

    @Query("SELECT id, display_name AS displayName, mime_type AS mimeType, size_bytes AS sizeBytes, created_at AS createdAt FROM files WHERE display_name LIKE '%' || :query || '%' AND upload_status = 'completed' AND deleted_at IS NULL")
    fun searchFiles(query: String): Flow<List<FileSummary>>
}
