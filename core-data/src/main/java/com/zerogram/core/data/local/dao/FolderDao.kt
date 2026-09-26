package com.zerogram.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.RewriteQueriesToDropUnusedColumns
import com.zerogram.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

data class FolderWithCount(
    val id: String,
    val name: String,
    @androidx.room.ColumnInfo(name = "parent_id") val parentId: String?,
    @androidx.room.ColumnInfo(name = "created_at") val createdAt: Long,
    @androidx.room.ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    val itemCount: Int
)

data class FolderStats(
    @androidx.room.ColumnInfo(name = "totalSize") val totalSize: Long,
    @androidx.room.ColumnInfo(name = "totalFolders") val totalFolders: Int,
    @androidx.room.ColumnInfo(name = "totalFiles") val totalFiles: Int
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE ((:parentId IS NULL AND parent_id IS NULL) OR (parent_id = :parentId)) AND deleted_at IS NULL")
    fun getFoldersByParentId(parentId: String?): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE ((:parentId IS NULL AND parent_id IS NULL) OR (parent_id = :parentId)) AND deleted_at IS NULL")
    suspend fun getFoldersByParentIdOnce(parentId: String?): List<FolderEntity>

    @Query("""
        SELECT f.*, 
        (
            (SELECT COUNT(*) FROM files WHERE folder_id = f.id AND deleted_at IS NULL) + 
            (SELECT COUNT(*) FROM folders WHERE parent_id = f.id AND deleted_at IS NULL)
        ) AS itemCount
        FROM folders f 
        WHERE ((:parentId IS NULL AND f.parent_id IS NULL) OR (f.parent_id = :parentId)) AND f.deleted_at IS NULL
    """)
    fun getFoldersWithCountByParentId(parentId: String?): Flow<List<FolderWithCount>>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("""
        WITH RECURSIVE folder_subtree AS (
            SELECT id FROM folders WHERE parent_id = :folderId AND deleted_at IS NULL
            UNION ALL
            SELECT child.id FROM folders child INNER JOIN folder_subtree parent ON child.parent_id = parent.id WHERE child.deleted_at IS NULL
        )
        SELECT 
            (SELECT COALESCE(SUM(size_bytes), 0) FROM files WHERE folder_id = :folderId AND upload_status = 'completed' AND deleted_at IS NULL)
            + (SELECT COALESCE(SUM(size_bytes), 0) FROM files WHERE folder_id IN (SELECT id FROM folder_subtree) AND upload_status = 'completed' AND deleted_at IS NULL) AS totalSize,
            (SELECT COUNT(*) FROM folder_subtree) AS totalFolders,
            (SELECT COUNT(*) FROM files WHERE folder_id = :folderId AND upload_status = 'completed' AND deleted_at IS NULL)
            + (SELECT COUNT(*) FROM files WHERE folder_id IN (SELECT id FROM folder_subtree) AND upload_status = 'completed' AND deleted_at IS NULL) AS totalFiles
    """)
    suspend fun getFolderSubtreeStats(folderId: String): FolderStats

    @Query("""
        WITH RECURSIVE folder_subtree AS (
            SELECT id FROM folders WHERE parent_id IN (:folderIds) AND deleted_at IS NULL
            UNION ALL
            SELECT child.id FROM folders child INNER JOIN folder_subtree parent ON child.parent_id = parent.id WHERE child.deleted_at IS NULL
        )
        SELECT 
            (SELECT COALESCE(SUM(size_bytes), 0) FROM files WHERE folder_id IN (:folderIds) AND upload_status = 'completed' AND deleted_at IS NULL)
            + (SELECT COALESCE(SUM(size_bytes), 0) FROM files WHERE folder_id IN (SELECT id FROM folder_subtree) AND upload_status = 'completed' AND deleted_at IS NULL) AS totalSize,
            (SELECT COUNT(*) FROM folder_subtree) AS totalFolders,
            (SELECT COUNT(*) FROM files WHERE folder_id IN (:folderIds) AND upload_status = 'completed' AND deleted_at IS NULL)
            + (SELECT COUNT(*) FROM files WHERE folder_id IN (SELECT id FROM folder_subtree) AND upload_status = 'completed' AND deleted_at IS NULL) AS totalFiles
    """)
    suspend fun getAggregateFolderSubtreeStats(folderIds: List<String>): FolderStats

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("UPDATE folders SET parent_id = :targetFolderId WHERE id IN (:folderIds)")
    suspend fun moveFolders(folderIds: List<String>, targetFolderId: String?)

    @Query("UPDATE folders SET deleted_at = :timestamp WHERE id IN (:folderIds)")
    suspend fun moveFoldersToTrash(folderIds: List<String>, timestamp: Long)

    @Query("SELECT * FROM folders WHERE deleted_at IS NOT NULL")
    fun getDeletedFolders(): Flow<List<FolderEntity>>

    @Query("SELECT COUNT(*) FROM folders WHERE deleted_at IS NOT NULL")
    fun getDeletedFoldersCount(): Flow<Int>

    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT f.*, 
            (SELECT COUNT(*) FROM files WHERE folder_id = f.id AND deleted_at IS NULL) + 
            (SELECT COUNT(*) FROM folders WHERE parent_id = f.id AND deleted_at IS NULL) AS itemCount 
        FROM folders f WHERE f.deleted_at IS NOT NULL
    """)
    fun getDeletedFoldersWithCount(): Flow<List<FolderWithCount>>

    @Query("DELETE FROM folders WHERE id IN (:folderIds)")
    suspend fun deleteFoldersPermanently(folderIds: List<String>)

    @Query("UPDATE folders SET deleted_at = NULL WHERE id IN (:folderIds)")
    suspend fun recoverFolders(folderIds: List<String>)

    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT f.*, 
            (SELECT COUNT(*) FROM files WHERE folder_id = f.id AND deleted_at IS NULL) + 
            (SELECT COUNT(*) FROM folders WHERE parent_id = f.id AND deleted_at IS NULL) AS itemCount 
        FROM folders f WHERE f.name LIKE '%' || :query || '%' AND f.deleted_at IS NULL
    """)
    fun searchFoldersWithCount(query: String): Flow<List<FolderWithCount>>
}
