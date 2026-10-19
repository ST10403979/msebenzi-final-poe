package com.msebenzi.app.data

/**
 * An in-app "targeted job alert" — see backend/utils/notify.js for how
 * these get created server-side (only for workers whose skills match a
 * job's category, or for the household/worker involved in a job update).
 */
data class AppNotification(
    val id: Int,
    val userId: Int,
    val type: String, // JOB_MATCH | JOB_ACCEPTED | JOB_COMPLETED | RATING_RECEIVED
    val jobId: Int? = null,
    val message: String,
    val isRead: Boolean,
    val createdAt: String? = null
)
