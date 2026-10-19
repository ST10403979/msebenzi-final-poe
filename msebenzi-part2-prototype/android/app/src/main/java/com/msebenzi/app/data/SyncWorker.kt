package com.msebenzi.app.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.msebenzi.app.notify.NotificationPoller

/**
 * Runs periodically (and immediately when connectivity returns - see MsebenziApp) to:
 *  1. replay any queued offline actions (job accepted/completed/rated/posted with no signal);
 *  2. check for new targeted job alerts and show them as system notifications, so alerts
 *     still arrive when Firebase push is not configured or the app is closed.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val session = SessionManager(applicationContext)
        val token = session.getBearerToken() ?: return Result.success() // not logged in, nothing to do
        val repository = JobRepository(applicationContext)

        try {
            val synced = repository.flushPending(token)
            if (synced > 0) Log.i("SyncWorker", "Synced $synced offline action(s)")
        } catch (e: Exception) {
            Log.w("SyncWorker", "Sync attempt failed, will retry later: ${e.message}")
        }

        try {
            NotificationPoller.checkForNewAlerts(applicationContext)
        } catch (e: Exception) {
            Log.w("SyncWorker", "Alert check failed: ${e.message}")
        }

        return if (repository.pendingCount() > 0) Result.retry() else Result.success()
    }
}
