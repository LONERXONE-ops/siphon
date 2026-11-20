package com.lonerxone.siphon.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.lonerxone.siphon.data.db.entity.DownloadItem
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM download_items ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE status IN ('QUEUED', 'DOWNLOADING', 'MOVING') ORDER BY position ASC")
    fun getActiveDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE status IN ('WAITING', 'PAUSED_NO_WIFI', 'PAUSED_NO_STORAGE', 'PAUSED_BY_USER') ORDER BY position ASC")
    fun getWaitlistDownloads(): Flow<List<DownloadItem>>

    /**
     * CRITICAL INVARIANT QUERY:
     * Only selects items ready for auto-start. Items marked pausedByUser = 1 are strictly excluded.
     */
    @Query("""
        SELECT * FROM download_items 
        WHERE status IN ('WAITING', 'PAUSED_NO_WIFI', 'PAUSED_NO_STORAGE') 
          AND pausedByUser = 0 
        ORDER BY position ASC
    """)
    suspend fun getSchedulableItems(): List<DownloadItem>

    /**
     * Startup & Boot Normalization:
     * Resets items that were in active states when process was killed back to WAITING.
     * Items with pausedByUser = 1 are strictly left untouched.
     */
    @Query("""
        UPDATE download_items 
        SET status = 'WAITING' 
        WHERE status IN ('DOWNLOADING', 'QUEUED', 'MOVING') 
          AND pausedByUser = 0
    """)
    suspend fun normalizeStuckDownloads()

    @Query("SELECT * FROM download_items WHERE status = 'COMPLETED' ORDER BY completedAt DESC LIMIT :limit")
    fun getRecentCompleted(limit: Int = 10): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE status = 'COMPLETED' ORDER BY completedAt DESC")
    fun getAllCompleted(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE id = :id")
    suspend fun getById(id: String): DownloadItem?

    @Query("SELECT * FROM download_items WHERE id = :id")
    fun getByIdFlow(id: String): Flow<DownloadItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DownloadItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DownloadItem>)

    @Update
    suspend fun update(item: DownloadItem)

    @Query("""
        UPDATE download_items 
        SET bytesDownloaded = :bytes, totalBytes = :total, progress = :progress, speedBps = :speed, etaSeconds = :eta 
        WHERE id = :id
    """)
    suspend fun updateProgress(id: String, bytes: Long, total: Long, progress: Float, speed: Long, eta: Long)

    @Query("UPDATE download_items SET status = :status, pausedByUser = :pausedByUser, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, pausedByUser: Boolean, errorMessage: String? = null)

    @Query("""
        UPDATE download_items 
        SET status = 'PAUSED_BY_USER', pausedByUser = 1 
        WHERE status IN ('WAITING', 'QUEUED', 'DOWNLOADING', 'PAUSED_NO_WIFI', 'PAUSED_NO_STORAGE')
    """)
    suspend fun pauseAll()

    @Query("""
        UPDATE download_items 
        SET status = 'WAITING', pausedByUser = 0 
        WHERE status = 'PAUSED_BY_USER'
    """)
    suspend fun resumeAll()

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM download_items WHERE status = 'DOWNLOADING'")
    suspend fun getActiveDownloadCount(): Int

    @Query("SELECT MAX(position) FROM download_items")
    suspend fun getMaxPosition(): Int?
}
