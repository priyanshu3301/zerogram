package com.zerogram.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zerogram.data.local.entity.TransferJobEntity
import kotlinx.coroutines.flow.Flow

data class TransferJobWithFile(
    @androidx.room.Embedded val job: TransferJobEntity,
    @androidx.room.ColumnInfo(name = "display_name") val displayName: String,
    @androidx.room.ColumnInfo(name = "mime_type") val mimeType: String
)

@Dao
interface TransferJobDao {
    @Query("SELECT * FROM transfer_jobs WHERE status = :status")
    fun getJobsByStatus(status: String): Flow<List<TransferJobEntity>>

    @Query("SELECT * FROM transfer_jobs WHERE type = :type ORDER BY created_at ASC")
    fun observeJobsByType(type: String): Flow<List<TransferJobEntity>>

    @Query("""
        SELECT tj.*, f.display_name, f.mime_type 
        FROM transfer_jobs tj 
        INNER JOIN files f ON tj.file_id = f.id 
        WHERE tj.type = :type 
    """)
    fun observeJobsWithFilesByType(type: String): Flow<List<TransferJobWithFile>>

    @Query("SELECT * FROM transfer_jobs WHERE type = :type AND status = 'queued' ORDER BY created_at ASC LIMIT 1")
    suspend fun getNextPendingJob(type: String): TransferJobEntity?

    @Query("SELECT * FROM transfer_jobs WHERE type = :type AND (status = 'uploading' OR status = 'downloading')")
    suspend fun getActiveJobs(type: String): List<TransferJobEntity>

    @Query("UPDATE transfer_jobs SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateJobStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transfer_jobs SET status = :status, updated_at = :updatedAt WHERE id IN (:ids)")
    suspend fun updateJobStatuses(ids: List<String>, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transfer_jobs SET status = :targetStatus, updated_at = :updatedAt WHERE type = :type AND status IN (:currentStatuses)")
    suspend fun updateJobsStatusByTypeAndCurrentStatuses(type: String, targetStatus: String, currentStatuses: List<String>, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transfer_jobs WHERE type = :type AND status IN (:statuses)")
    suspend fun getJobsByTypeAndStatuses(type: String, statuses: List<String>): List<TransferJobEntity>

    @Query("SELECT * FROM transfer_jobs WHERE id = :id")
    suspend fun getJobById(id: String): TransferJobEntity?

    @Query("DELETE FROM transfer_jobs WHERE id IN (:ids)")
    suspend fun deleteJobs(ids: List<String>)

    @Query("DELETE FROM transfer_jobs WHERE type = :type AND status IN (:statuses)")
    suspend fun deleteJobsByTypeAndStatuses(type: String, statuses: List<String>)

    @Query("SELECT * FROM transfer_jobs WHERE file_id = :fileId AND type = :type LIMIT 1")
    suspend fun getJobByFileIdAndType(fileId: String, type: String): TransferJobEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: TransferJobEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJobs(jobs: List<TransferJobEntity>)

    @Update
    suspend fun updateJob(job: TransferJobEntity)

    @Query("DELETE FROM transfer_jobs WHERE id = :id")
    suspend fun deleteJob(id: String)
}
