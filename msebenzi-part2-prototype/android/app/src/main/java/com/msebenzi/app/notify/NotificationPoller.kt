package com.msebenzi.app.notify

import android.content.Context
import android.util.Log
import com.msebenzi.app.data.RetrofitClient
import com.msebenzi.app.data.SessionManager

/**
 * Polls the API for unread targeted alerts and shows each new one as a system notification.
 *
 * Why polling as well as Firebase push? Firebase Cloud Messaging needs a Firebase project
 * (google-services.json). Polling means alerts still arrive without it - every ~20 s while the
 * app is open (MainActivity) and every 15 min in the background (SyncWorker) - so the
 * notification feature always works, and FCM simply makes it instant when it is configured.
 */
object NotificationPoller {

    private const val TAG = "NotificationPoller"

    /** Returns the number of unread alerts (for the badge), or -1 if it could not be fetched. */
    suspend fun checkForNewAlerts(context: Context): Int {
        val appContext = context.applicationContext
        val session = SessionManager(appContext)
        val token = session.getBearerToken() ?: return -1

        val response = try {
            RetrofitClient.api.getNotifications(token)
        } catch (e: Exception) {
            Log.d(TAG, "Could not fetch alerts: ${e.message}")
            return -1
        }
        val alerts = response.body()
        if (!response.isSuccessful || alerts == null) return -1

        val unread = alerts.count { !it.isRead }
        val newestId = alerts.maxOfOrNull { it.id } ?: 0
        val lastNotified = session.getLastNotifiedId()

        if (lastNotified < 0) {
            // First ever poll for this login: remember where we are, don't flood with old alerts.
            session.setLastNotifiedId(newestId)
            return unread
        }

        if (session.notificationsEnabled()) {
            alerts.filter { !it.isRead && it.id > lastNotified }
                .sortedBy { it.id }
                .forEach { alert ->
                    NotificationHelper.show(
                        appContext, alert.id, NotificationHelper.titleFor(appContext, alert.type),
                        alert.message, alert.jobId
                    )
                }
        }
        if (newestId > lastNotified) session.setLastNotifiedId(newestId)
        return unread
    }
}
