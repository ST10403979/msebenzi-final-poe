package com.msebenzi.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Dao
interface JobDao {

    @Query("SELECT * FROM jobs ORDER BY isPendingSync DESC, id DESC")
    suspend fun getAllJobs(): List<Job>

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    suspend fun getJob(id: Int): Job?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(jobs: List<Job>)

    /** Clears the cached feed but keeps jobs that still wait to be uploaded. */
    @Query("DELETE FROM jobs WHERE isPendingSync = 0")
    suspend fun clearSynced()

    @Query("DELETE FROM jobs WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM jobs")
    suspend fun clearAll()

    /** Optimistic local update used when a worker accepts a job while offline. */
    @Query("UPDATE jobs SET status = 'accepted', acceptedById = :userId, acceptedByName = :userName, isPendingSync = 1 WHERE id = :id")
    suspend fun markAcceptedLocally(id: Int, userId: Int, userName: String?)

    /** Optimistic local update used when a job is completed while offline. */
    @Query("UPDATE jobs SET status = 'completed', isPendingSync = 1 WHERE id = :id")
    suspend fun markCompletedLocally(id: Int)

    /** Replaces the cached feed with a fresh one from the API. */
    suspend fun replaceAll(jobs: List<Job>) {
        clearSynced()
        insertAll(jobs)
    }
}

/**
 * Represents an action taken while offline (e.g. "accept this job"), queued for
 * WorkManager to replay once connectivity returns - the "sync outbox" from Part 1.
 *
 * actionType: ACCEPT_JOB | COMPLETE_JOB | RATE_JOB | CREATE_JOB
 * payload:    JSON with extra data (rating score/comment, or the job to create)
 */
@Entity(tableName = "sync_actions")
data class SyncAction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionType: String,
    val targetJobId: Int,
    val payload: String? = null,
    val synced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface SyncActionDao {

    @Insert
    suspend fun enqueue(action: SyncAction): Long

    @Query("SELECT * FROM sync_actions WHERE synced = 0 ORDER BY createdAt ASC")
    suspend fun getPending(): List<SyncAction>

    @Query("SELECT COUNT(*) FROM sync_actions WHERE synced = 0")
    suspend fun pendingCount(): Int

    @Query("UPDATE sync_actions SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Int)

    @Query("DELETE FROM sync_actions")
    suspend fun clearAll()
}
