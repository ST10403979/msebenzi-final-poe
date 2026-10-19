package com.msebenzi.app.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * Unit tests for the offline-first repository. The API and DAOs are mocked so these run on
 * the JVM in milliseconds (and in GitHub Actions) with no emulator.
 */
class JobRepositoryTest {

    // relaxUnitFun = true: suspend functions returning Unit (insertAll, markSynced ...)
    // don't need to be stubbed individually.
    private val api: ApiService = mockk()
    private val jobDao: JobDao = mockk(relaxUnitFun = true)
    private val syncActionDao: SyncActionDao = mockk(relaxUnitFun = true)

    private lateinit var repository: JobRepository

    private val sampleDto = JobDto(
        id = 1,
        title = "Mow the lawn",
        description = "Front and back garden",
        category = "gardening",
        location = "Cape Town",
        budget = 150.0,
        status = "open",
        postedById = 2,
        postedBy = PersonRef(2, "Thandi")
    )
    private val sampleJobs = listOf(sampleDto.toJob())

    @Before
    fun setUp() {
        coEvery { syncActionDao.getPending() } returns emptyList()
        coEvery { jobDao.getAllJobs() } returns sampleJobs
        repository = JobRepository(jobDao, syncActionDao, api)
    }

    // ------------------------------------------------------------ reading the feed

    @Test
    fun `getJobFeed refreshes the local cache and returns jobs on success`() = runTest {
        coEvery { api.getJobs(any(), any()) } returns Response.success(listOf(sampleDto))

        val result = repository.getJobFeed("Bearer token")

        assertTrue(result.isSuccess)
        assertEquals(sampleJobs, result.getOrNull())
        coVerify(exactly = 1) { jobDao.replaceAll(sampleJobs) }
    }

    @Test
    fun `getJobFeed falls back to the local cache when the network call fails`() = runTest {
        coEvery { api.getJobs(any(), any()) } throws IOException("no network")

        val result = repository.getJobFeed("Bearer token")

        assertTrue(result.isSuccess)
        assertEquals(sampleJobs, result.getOrNull())
        coVerify(exactly = 0) { jobDao.replaceAll(any()) }
    }

    @Test
    fun `getJobFeed reports an expired session on HTTP 401`() = runTest {
        coEvery { api.getJobs(any(), any()) } returns Response.error(401, "".toResponseBody())

        val result = repository.getJobFeed("Bearer token")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnauthorizedException)
    }

    @Test
    fun `getJob uses the cached copy when offline`() = runTest {
        coEvery { api.getJob(any(), any()) } throws IOException("no network")
        coEvery { jobDao.getJob(1) } returns sampleJobs.first()

        val result = repository.getJob("Bearer token", 1)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
    }

    // ---------------------------------------------------------- accepting a job

    @Test
    fun `acceptJob calls the API directly when online`() = runTest {
        coEvery { api.acceptJob(any(), any()) } returns Response.success(mapOf("message" to "ok"))

        val result = repository.acceptJob("Bearer token", jobId = 1)

        assertTrue(result.isSuccess)
        assertEquals(SyncOutcome.DONE, result.getOrNull())
        coVerify(exactly = 0) { syncActionDao.enqueue(any()) }
    }

    @Test
    fun `acceptJob queues a sync action and updates the cache when the device is offline`() = runTest {
        coEvery { api.acceptJob(any(), any()) } throws IOException("no network")
        coEvery { syncActionDao.enqueue(any()) } returns 1L

        val result = repository.acceptJob("Bearer token", jobId = 1, userId = 7, userName = "Sipho")

        assertTrue(result.isSuccess)
        assertEquals(SyncOutcome.QUEUED, result.getOrNull())
        coVerify {
            syncActionDao.enqueue(match { it.actionType == "ACCEPT_JOB" && it.targetJobId == 1 })
        }
        coVerify { jobDao.markAcceptedLocally(1, 7, "Sipho") }
    }

    @Test
    fun `acceptJob surfaces a conflict when someone else already took the job`() = runTest {
        coEvery { api.acceptJob(any(), any()) } returns Response.error(409, "".toResponseBody())

        val result = repository.acceptJob("Bearer token", jobId = 1)

        assertTrue(result.isFailure)
        assertEquals(409, (result.exceptionOrNull() as ApiException).code)
        coVerify(exactly = 0) { syncActionDao.enqueue(any()) }
    }

    // -------------------------------------------------- completing / rating / posting

    @Test
    fun `completeJob is queued when offline`() = runTest {
        coEvery { api.completeJob(any(), any()) } throws IOException("no network")
        coEvery { syncActionDao.enqueue(any()) } returns 2L

        val result = repository.completeJob("Bearer token", 4)

        assertEquals(SyncOutcome.QUEUED, result.getOrNull())
        coVerify { syncActionDao.enqueue(match { it.actionType == "COMPLETE_JOB" && it.targetJobId == 4 }) }
        coVerify { jobDao.markCompletedLocally(4) }
    }

    @Test
    fun `rateJob is queued with its score when offline`() = runTest {
        coEvery { api.rateJob(any(), any(), any()) } throws IOException("no network")
        coEvery { syncActionDao.enqueue(any()) } returns 3L

        val result = repository.rateJob("Bearer token", 4, 5, "Great work")

        assertEquals(SyncOutcome.QUEUED, result.getOrNull())
        coVerify {
            syncActionDao.enqueue(match {
                it.actionType == "RATE_JOB" && it.payload!!.contains("\"score\":5")
            })
        }
    }

    @Test
    fun `createJob offline stores a pending local job and queues the upload`() = runTest {
        coEvery { api.createJob(any(), any()) } throws IOException("no network")
        coEvery { syncActionDao.enqueue(any()) } returns 5L

        val result = repository.createJob(
            "Bearer token",
            CreateJobRequest("Paint fence", "Two coats", "painting", "Gqeberha", 800.0),
            userId = 9
        )

        val job = result.getOrNull()!!
        assertTrue(job.isPendingSync)
        assertTrue(job.id < 0) // temporary local id
        assertEquals(9, job.postedById)
        coVerify { jobDao.insertAll(match { it.single().isPendingSync }) }
        coVerify { syncActionDao.enqueue(match { it.actionType == "CREATE_JOB" && it.targetJobId == job.id }) }
    }

    @Test
    fun `createJob online returns the server job and caches it`() = runTest {
        coEvery { api.createJob(any(), any()) } returns Response.success(201, sampleDto)

        val result = repository.createJob(
            "Bearer token",
            CreateJobRequest("Mow the lawn", "Front and back garden", "gardening", "Cape Town", 150.0)
        )

        assertTrue(result.isSuccess)
        assertFalse(result.getOrNull()!!.isPendingSync)
        coVerify(exactly = 0) { syncActionDao.enqueue(any()) }
    }

    // ------------------------------------------------------------------- syncing

    @Test
    fun `flushPending replays queued actions and marks them synced`() = runTest {
        coEvery { syncActionDao.getPending() } returns listOf(
            SyncAction(id = 5, actionType = "ACCEPT_JOB", targetJobId = 3),
            SyncAction(id = 6, actionType = "COMPLETE_JOB", targetJobId = 3)
        )
        coEvery { api.acceptJob(any(), 3) } returns Response.success(mapOf("message" to "ok"))
        coEvery { api.completeJob(any(), 3) } returns Response.success(mapOf("message" to "ok"))

        val synced = repository.flushPending("Bearer token")

        assertEquals(2, synced)
        coVerify { syncActionDao.markSynced(5) }
        coVerify { syncActionDao.markSynced(6) }
    }

    @Test
    fun `flushPending stops when still offline and keeps the actions queued`() = runTest {
        coEvery { syncActionDao.getPending() } returns listOf(
            SyncAction(id = 5, actionType = "ACCEPT_JOB", targetJobId = 3)
        )
        coEvery { api.acceptJob(any(), any()) } throws IOException("no network")

        val synced = repository.flushPending("Bearer token")

        assertEquals(0, synced)
        coVerify(exactly = 0) { syncActionDao.markSynced(any()) }
    }

    @Test
    fun `flushPending drops an action the server permanently rejects`() = runTest {
        coEvery { syncActionDao.getPending() } returns listOf(
            SyncAction(id = 8, actionType = "ACCEPT_JOB", targetJobId = 3)
        )
        coEvery { api.acceptJob(any(), any()) } returns Response.error(409, "".toResponseBody())

        assertEquals(1, repository.flushPending("Bearer token"))
        coVerify { syncActionDao.markSynced(8) }
    }

    @Test
    fun `isTerminal retries on 401 timeouts and server errors but not on other client errors`() {
        assertTrue(JobRepository.isTerminal(200))
        assertTrue(JobRepository.isTerminal(409))
        assertTrue(JobRepository.isTerminal(404))
        assertFalse(JobRepository.isTerminal(401))
        assertFalse(JobRepository.isTerminal(429))
        assertFalse(JobRepository.isTerminal(500))
        assertFalse(JobRepository.isTerminal(503))
    }
}
