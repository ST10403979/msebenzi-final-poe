package com.msebenzi.app.notify

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.msebenzi.app.R
import com.msebenzi.app.data.SessionManager

/**
 * Receives Firebase Cloud Messaging pushes. The API sends *data* messages
 * (title, body, jobId, notificationId) so this service always runs - in the foreground and
 * the background - and builds the notification itself, in the user's language settings.
 */
class MsebenziMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        PushRegistrar.onNewToken(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val session = SessionManager(applicationContext)
        if (!session.isLoggedIn() || !session.notificationsEnabled()) return

        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: getString(R.string.app_name)
        val body = data["body"] ?: message.notification?.body ?: return
        val alertId = data["notificationId"]?.toIntOrNull()
        val jobId = data["jobId"]?.toIntOrNull()

        // Same id as the poller uses, so the same alert is never shown twice.
        val id = alertId ?: (System.currentTimeMillis() % 100_000_000L).toInt()
        NotificationHelper.show(applicationContext, id, title, body, jobId)
        if (alertId != null && alertId > session.getLastNotifiedId()) session.setLastNotifiedId(alertId)
    }
}
