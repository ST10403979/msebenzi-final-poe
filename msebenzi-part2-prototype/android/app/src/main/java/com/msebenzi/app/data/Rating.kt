package com.msebenzi.app.data

/**
 * A rating left on a completed job. `rater` is included by the API so the
 * profile screen can show who left each review.
 */
data class Rating(
    val id: Int,
    val jobId: Int,
    val raterId: Int,
    val rateeId: Int,
    val score: Int,
    val comment: String? = null,
    val rater: RaterInfo? = null,
    val createdAt: String? = null
)

data class RaterInfo(
    val id: Int,
    val name: String
)

data class SubmitRatingRequest(
    val score: Int,
    val comment: String? = null
)
