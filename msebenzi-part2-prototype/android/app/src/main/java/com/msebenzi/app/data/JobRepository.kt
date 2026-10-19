package com.msebenzi.app.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.UUID

/** Thrown when the API answers 401 - the saved session has expired and the user must log in again. */
class UnauthorizedException : Exception("Session expired")

/** An HTTP error from the API (e.g. 409 = "this job is no longer available"). */
class ApiException(val code: Int) : Exception("Server error: $code")

/** Whether an action ran straight away (DONE) or was saved for later (QUEUED, device offline). */
enum class SyncOutcome { DONE, QUEUED }

/** Payload stored in the outbox for a job that was created while offline. */
data class PendingCreateJob(val request: CreateJobRequest, val photoPath: String?)

/**
 * Combines the local Room cache and the remote REST API, following the offline-first
 * repository pattern from the Part 1 design (local database = source of truth, the
 * network refreshes it).
 *
 * OFFLINE MODE WITH SYNC: accepting, completing and rating a job, and posting a new job,
 * all keep working with no signal. Each action is saved in the `sync_actions` outbox and
 * applied optimistically to the local cache; [flushPending] replays the outbox in order
 * once the device is back online (triggered by SyncWorker / a connectivity callback).
 *
 * Dependencies are injected through the primary constructor so unit tests can supply
 * mocks (see JobRepositoryTest); the secondary constructor keeps `JobRepository(context)`
 * working at call sites.
 */
class JobRepository(
    private val jobDao: JobDao,
    private val syncActionDao: SyncActionDao,
    private val api: ApiService,
    private val gson: Gson = Gson()
) {
    constructor(context: Context) : this(
        AppDatabase.getInstance(context).jobDao(),
        AppDatabase.getInstance(context).syncActionDao(),
        RetrofitClient.api
    )

    companion object {
        private const val TAG = "JobRepository"
        const val ACTION_ACCEPT = "ACCEPT_JOB"
        const val ACTION_COMPLETE = "COMPLETE_JOB"
        const val ACTION_RATE = "RATE_JOB"
        const val ACTION_CREATE = "CREATE_JOB"
        private val JPEG = "image/jpeg".toMediaType()

        /** HTTP answers that mean "the server has decided" - the queued action can be dropped. */
        fun isTerminal(code: Int): Boolean =
            code in 200..299 || (code in 400..499 && code != 401 && code != 408 && code != 429)
    }

    // ------------------------------------------------------------------ reading

    /**
     * Tries the network first; on success it refreshes the local cache. On any failure
     * (no connectivity, server down, timeout) it falls back to whatever is cached locally
     * so the feed screen never shows a hard error for a connectivity blip.
     */
    suspend fun getJobFeed(token: String): Result<List<Job>> {
        flushPending(token)
        return try {
            val response = api.getJobs(token)
            val body = response.body()
            if (response.isSuccessful && body != null) {
                jobDao.replaceAll(body.map { it.toJob() })
                Result.success(jobDao.getAllJobs())
            } else if (response.code() == 401) {
                Result.failure(UnauthorizedException())
            } else {
                Log.w(TAG, "Server returned ${response.code()}, using local cache")
                Result.success(jobDao.getAllJobs())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Network error, falling back to local cache: ${e.message}")
            Result.success(jobDao.getAllJobs())
        }
    }

    /** One job: fresh from the API when online, otherwise from the local cache. */
    suspend fun getJob(token: String, id: Int): Result<Job> {
        if (id > 0) flushPending(token)
        return try {
            if (id < 0) {
                // A job that only exists on this phone (created offline).
                jobDao.getJob(id)?.let { Result.success(it) } ?: Result.failure(IOException("not found"))
            } else {
                val response = api.getJob(token, id)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    val job = body.toJob()
                    jobDao.insertAll(listOf(job))
                    Result.success(job)
                } else if (response.code() == 401) {
                    Result.failure(UnauthorizedException())
                } else {
                    jobDao.getJob(id)?.let { Result.success(it) } ?: Result.failure(ApiException(response.code()))
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Offline - using cached job $id: ${e.message}")
            jobDao.getJob(id)?.let { Result.success(it) } ?: Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun pendingCount(): Int = syncActionDao.pendingCount()

    // ------------------------------------------------------------------ writing

    /** Uploads image bytes as a BLOB on the API and returns the new media id. */
    suspend fun uploadMedia(token: String, jpegBytes: ByteArray): Result<Int> {
        return try {
            val response = api.uploadMedia(token, jpegBytes.toRequestBody(JPEG))
            val body = response.body()
            when {
                response.isSuccessful && body != null -> Result.success(body.id)
                response.code() == 401 -> Result.failure(UnauthorizedException())
                else -> Result.failure(ApiException(response.code()))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Posts a job. Offline, the job is stored on the phone (shown with a "waiting to sync"
     * tag) and uploaded later; the returned Job then has `isPendingSync = true`.
     */
    suspend fun createJob(
        token: String,
        request: CreateJobRequest,
        userId: Int = -1,
        photoBytes: ByteArray? = null,
        photoDir: File? = null
    ): Result<Job> {
        val clientRef = request.clientRef ?: UUID.randomUUID().toString()
        val base = request.copy(clientRef = clientRef)
        return try {
            var mediaId: Int? = null
            if (photoBytes != null) {
                // An IOException here (offline) falls through to the queue below.
                val upload = api.uploadMedia(token, photoBytes.toRequestBody(JPEG))
                if (upload.isSuccessful) mediaId = upload.body()?.id
                else if (upload.code() == 401) return Result.failure(UnauthorizedException())
            }
            val response = api.createJob(token, base.copy(photoMediaId = mediaId))
            val body = response.body()
            when {
                response.isSuccessful && body != null -> {
                    val job = body.toJob()
                    jobDao.insertAll(listOf(job))
                    Result.success(job)
                }
                response.code() == 401 -> Result.failure(UnauthorizedException())
                else -> Result.failure(ApiException(response.code()))
            }
        } catch (e: IOException) {
            Log.w(TAG, "Offline - saving new job for later sync")
            val localId = -(1 + (System.currentTimeMillis() % 1_000_000_000L).toInt())
            val photoPath = if (photoBytes != null && photoDir != null) {
                withContext(Dispatchers.IO) {
                    photoDir.mkdirs()
                    File(photoDir, "$clientRef.jpg").also { it.writeBytes(photoBytes) }.absolutePath
                }
            } else null
            val local = Job(
                id = localId, title = base.title, description = base.description,
                category = base.category, location = base.location, budget = base.budget,
                status = "open", postedById = userId, clientRef = clientRef, isPendingSync = true
            )
            jobDao.insertAll(listOf(local))
            syncActionDao.enqueue(
                SyncAction(
                    actionType = ACTION_CREATE,
                    targetJobId = localId,
                    payload = gson.toJson(PendingCreateJob(base, photoPath))
                )
            )
            Result.success(local)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Accepts a job. If the device is offline, the action is queued in the sync outbox
     * instead of failing - WorkManager (SyncWorker) replays it once connectivity returns.
     */
    suspend fun acceptJob(token: String, jobId: Int, userId: Int = -1, userName: String? = null): Result<SyncOutcome> {
        return try {
            val response = api.acceptJob(token, jobId)
            when {
                response.isSuccessful -> Result.success(SyncOutcome.DONE)
                response.code() == 401 -> Result.failure(UnauthorizedException())
                else -> Result.failure(ApiException(response.code()))
            }
        } catch (e: IOException) {
            Log.w(TAG, "Offline - queuing accept action for job $jobId")
            syncActionDao.enqueue(SyncAction(actionType = ACTION_ACCEPT, targetJobId = jobId))
            jobDao.markAcceptedLocally(jobId, userId, userName)
            Result.success(SyncOutcome.QUEUED)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeJob(token: String, jobId: Int): Result<SyncOutcome> {
        return try {
            val response = api.completeJob(token, jobId)
            when {
                response.isSuccessful -> Result.success(SyncOutcome.DONE)
                response.code() == 401 -> Result.failure(UnauthorizedException())
                else -> Result.failure(ApiException(response.code()))
            }
        } catch (e: IOException) {
            Log.w(TAG, "Offline - queuing complete action for job $jobId")
            syncActionDao.enqueue(SyncAction(actionType = ACTION_COMPLETE, targetJobId = jobId))
            jobDao.markCompletedLocally(jobId)
            Result.success(SyncOutcome.QUEUED)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun rateJob(token: String, jobId: Int, score: Int, comment: String?): Result<SyncOutcome> {
        val body = SubmitRatingRequest(score = score, comment = comment)
        return try {
            val response = api.rateJob(token, jobId, body)
            when {
                response.isSuccessful -> Result.success(SyncOutcome.DONE)
                response.code() == 401 -> Result.failure(UnauthorizedException())
                else -> Result.failure(ApiException(response.code()))
            }
        } catch (e: IOException) {
            Log.w(TAG, "Offline - queuing rating for job $jobId")
            syncActionDao.enqueue(
                SyncAction(actionType = ACTION_RATE, targetJobId = jobId, payload = gson.toJson(body))
            )
            Result.success(SyncOutcome.QUEUED)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --------------------------------------------------------------------- sync

    /**
     * Replays the offline outbox in the order the actions happened. Stops at the first
     * network failure (still offline) so ordering is preserved. Returns how many actions
     * were resolved.
     */
    suspend fun flushPending(token: String): Int {
        var resolved = 0
        try {
            for (action in syncActionDao.getPending()) {
                val done = try {
                    replay(token, action)
                } catch (e: IOException) {
                    Log.w(TAG, "Still offline, will retry later: ${e.message}")
                    break
                }
                if (done) {
                    syncActionDao.markSynced(action.id)
                    resolved++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sync attempt failed, will retry later: ${e.message}")
        }
        return resolved
    }

    /** Replays one action. Returns true when it is resolved (success or a permanent rejection). */
    private suspend fun replay(token: String, action: SyncAction): Boolean {
        return when (action.actionType) {
            ACTION_ACCEPT -> isTerminal(api.acceptJob(token, action.targetJobId).code())
            ACTION_COMPLETE -> isTerminal(api.completeJob(token, action.targetJobId).code())
            ACTION_RATE -> {
                val body = gson.fromJson(action.payload, SubmitRatingRequest::class.java)
                    ?: return true // corrupted payload - drop it
                isTerminal(api.rateJob(token, action.targetJobId, body).code())
            }
            ACTION_CREATE -> replayCreate(token, action)
            else -> {
                Log.w(TAG, "Unknown action type: ${action.actionType}")
                true
            }
        }
    }

    private suspend fun replayCreate(token: String, action: SyncAction): Boolean {
        val pending = try {
            gson.fromJson(action.payload, PendingCreateJob::class.java)
        } catch (e: Exception) {
            null
        } ?: return true // corrupted payload - drop it

        var mediaId: Int? = null
        val photoFile = pending.photoPath?.let { File(it) }
        if (photoFile != null && photoFile.exists()) {
            val bytes = withContext(Dispatchers.IO) { photoFile.readBytes() }
            val upload = api.uploadMedia(token, bytes.toRequestBody(JPEG))
            if (upload.isSuccessful) mediaId = upload.body()?.id
            else if (!isTerminal(upload.code())) return false
        }
        val response = api.createJob(token, pending.request.copy(photoMediaId = mediaId))
        if (!isTerminal(response.code())) return false

        // The real job now exists on the server - drop the temporary local copy.
        jobDao.deleteById(action.targetJobId)
        if (photoFile != null) withContext(Dispatchers.IO) { photoFile.delete() }
        return true
    }
}
