package com.msebenzi.app.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.msebenzi.app.JobDetailActivity
import com.msebenzi.app.MainActivity
import com.msebenzi.app.R

/**
 * Builds and shows system notifications (the "real-time notification" feature).
 * Used by both the Firebase push service and the in-app alert poller.
 *
 * References:
 *  - Android Developers (2026) Create a notification.
 *  - Android Developers (2026) Notification runtime permission (Android 13+).
 */
object NotificationHelper {

    const val CHANNEL_ID = "msebenzi_alerts"

    /** Creates the notification channel (required on Android 8+). Safe to call repeatedly. */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.channel_description)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Human-readable, localised heading for an alert type from the API. */
    fun titleFor(context: Context, type: String): String = context.getString(
        when (type) {
            "JOB_MATCH" -> R.string.alert_type_job_match
            "JOB_ACCEPTED" -> R.string.alert_type_job_accepted
            "JOB_COMPLETED" -> R.string.alert_type_job_completed
            "RATING_RECEIVED" -> R.string.alert_type_rating
            else -> R.string.alert_type_generic
        }
    )

    /**
     * Shows a notification. Re-using the same [id] updates the existing notification instead
     * of stacking a duplicate (so a push and a poll for the same alert show once).
     * Tapping it opens the related job, or the home screen when there is no job.
     */
    fun show(context: Context, id: Int, title: String, body: String, jobId: Int?) {
        if (!hasPermission(context)) return

        val intent = if (jobId != null && jobId > 0) {
            Intent(context, JobDetailActivity::class.java).putExtra(JobDetailActivity.EXTRA_JOB_ID, jobId)
        } else {
            Intent(context, MainActivity::class.java)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        val pendingIntent = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_alerts)
            .setColor(ContextCompat.getColor(context, R.color.msebenzi_green))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call - safe to ignore.
        }
    }
}
