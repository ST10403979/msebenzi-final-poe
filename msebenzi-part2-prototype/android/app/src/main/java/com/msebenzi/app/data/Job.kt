package com.msebenzi.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single job card. This class is the Room entity cached locally so the job feed
 * still works offline (see AppDatabase / JobDao). The network model is [JobDto].
 *
 * Jobs created while offline get a temporary *negative* id and `isPendingSync = true`
 * until the SyncWorker has uploaded them.
 */
@Entity(tableName = "jobs")
data class Job(
    @PrimaryKey
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val budget: Double,
    val status: String, // "open" | "accepted" | "completed" | "cancelled"
    val postedById: Int,
    val acceptedById: Int? = null,
    val createdAt: String? = null,
    val photoMediaId: Int? = null,
    val clientRef: String? = null,
    val postedByName: String? = null,
    val acceptedByName: String? = null,
    val isPendingSync: Boolean = false
)

/** A person reference embedded in API job payloads: `{ "id": 1, "name": "Thabo" }`. */
data class PersonRef(val id: Int, val name: String)

/** JSON model returned by the REST API (Gson deserialises into this). */
data class JobDto(
    val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val budget: Double,
    val status: String,
    val postedById: Int,
    val acceptedById: Int? = null,
    val createdAt: String? = null,
    val photoMediaId: Int? = null,
    val clientRef: String? = null,
    val postedBy: PersonRef? = null,
    val acceptedBy: PersonRef? = null
) {
    fun toJob(): Job = Job(
        id = id,
        title = title,
        description = description,
        category = category,
        location = location,
        budget = budget,
        status = status,
        postedById = postedById,
        acceptedById = acceptedById,
        createdAt = createdAt,
        photoMediaId = photoMediaId,
        clientRef = clientRef,
        postedByName = postedBy?.name,
        acceptedByName = acceptedBy?.name,
        isPendingSync = false
    )
}

/**
 * Request body for creating a job (household -> POST /api/jobs).
 * `clientRef` is a random id made on the phone so a retry after a flaky connection
 * can never create the same job twice (the API de-duplicates on it).
 */
data class CreateJobRequest(
    val title: String,
    val description: String,
    val category: String,
    val location: String,
    val budget: Double,
    val photoMediaId: Int? = null,
    val clientRef: String? = null
)
