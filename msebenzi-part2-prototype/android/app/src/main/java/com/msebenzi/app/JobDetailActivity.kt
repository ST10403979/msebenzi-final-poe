package com.msebenzi.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.msebenzi.app.data.ApiException
import com.msebenzi.app.data.Job
import com.msebenzi.app.data.JobRepository
import com.msebenzi.app.data.ServiceCatalog
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.data.SyncOutcome
import com.msebenzi.app.data.UnauthorizedException
import com.msebenzi.app.databinding.ActivityJobDetailBinding
import com.msebenzi.app.databinding.DialogRateJobBinding
import com.msebenzi.app.util.ImageUtils.loadMedia
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Full details of one job, with the actions that make sense for the current user:
 * workers can accept an open job; the household or the accepted worker can complete it;
 * either side can rate the other once it is completed. Every action works offline (queued).
 */
class JobDetailActivity : BaseActivity() {

    private lateinit var binding: ActivityJobDetailBinding
    private lateinit var session: SessionManager
    private lateinit var repository: JobRepository
    private var jobId: Int = 0
    private var currentJob: Job? = null

    companion object {
        const val EXTRA_JOB_ID = "extra_job_id"
        private const val TAG = "JobDetailActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJobDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.headerSection, top = true)
        applyInsets(binding.scroll, bottom = true)

        session = SessionManager(this)
        repository = JobRepository(this)
        jobId = intent.getIntExtra(EXTRA_JOB_ID, 0)

        if (jobId == 0) {
            finish()
            return
        }

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonAccept.setOnClickListener { acceptJob() }
        binding.buttonComplete.setOnClickListener { completeJob() }
        binding.buttonRate.setOnClickListener { showRateDialog() }
        binding.buttonViewProfile.setOnClickListener { openOtherPartyProfile() }

        loadJobDetails()
    }

    private fun loadJobDetails() {
        val token = session.getBearerToken() ?: return forceLogout()
        lifecycleScope.launch {
            repository.getJob(token, jobId)
                .onSuccess { job ->
                    currentJob = job
                    bindJob(job)
                }
                .onFailure { e ->
                    Log.w(TAG, "Failed to load job: ${e.message}")
                    if (e is UnauthorizedException) forceLogout() else showMessage(getString(R.string.error_loading_job))
                }
        }
    }

    private fun bindJob(job: Job) {
        val service = ServiceCatalog.find(job.category)
        val serviceName = service?.let { getString(it.nameRes) } ?: job.category

        binding.imageHero.loadMedia(job.photoMediaId, ServiceCatalog.imageFor(job.category))
        binding.textTitle.text = job.title
        binding.textCategoryLocation.text = "$serviceName • ${job.location}"
        binding.textBudget.text = String.format(Locale.getDefault(), "R%.2f", job.budget)
        binding.textDescription.text = job.description
        binding.textPostedBy.text = job.postedByName?.let { getString(R.string.posted_by, it) }.orEmpty()
        binding.textPostedBy.visibility = if (job.postedByName == null) View.GONE else View.VISIBLE

        val (labelRes, colorRes) = when (job.status) {
            "open" -> R.string.status_open to R.color.status_open
            "accepted" -> R.string.status_accepted to R.color.status_accepted
            "completed" -> R.string.status_completed to R.color.status_completed
            else -> R.string.status_cancelled to R.color.status_cancelled
        }
        binding.textStatus.setText(labelRes)
        binding.textStatus.backgroundTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))

        val myId = session.getUserId()
        val isParticipant = myId == job.postedById || myId == job.acceptedById
        val onServer = job.id > 0 // jobs created offline don't exist on the server yet

        binding.buttonAccept.visibility =
            if (job.status == "open" && session.isWorker() && job.postedById != myId && onServer) View.VISIBLE else View.GONE
        binding.buttonAccept.isEnabled = true
        binding.buttonComplete.visibility =
            if (job.status == "accepted" && isParticipant && onServer) View.VISIBLE else View.GONE
        binding.buttonComplete.isEnabled = true
        binding.buttonRate.visibility =
            if (job.status == "completed" && isParticipant && onServer) View.VISIBLE else View.GONE
        binding.buttonViewProfile.visibility =
            if (isParticipant && job.acceptedById != null && onServer) View.VISIBLE else View.GONE

        if (job.isPendingSync) showMessage(getString(R.string.waiting_to_sync_detail))
    }

    private fun acceptJob() {
        val token = session.getBearerToken() ?: return forceLogout()
        binding.buttonAccept.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = repository.acceptJob(token, jobId, session.getUserId(), session.getName())
            binding.progressBar.visibility = View.GONE
            result
                .onSuccess { outcome ->
                    showMessage(getString(if (outcome == SyncOutcome.QUEUED) R.string.saved_offline_will_sync else R.string.job_accepted))
                    loadJobDetails()
                }
                .onFailure { e ->
                    Log.e(TAG, "Accept job failed", e)
                    when {
                        e is UnauthorizedException -> forceLogout()
                        e is ApiException && e.code == 409 -> showMessage(getString(R.string.error_job_taken))
                        else -> showMessage(getString(R.string.error_accepting_job))
                    }
                    binding.buttonAccept.isEnabled = true
                }
        }
    }

    private fun completeJob() {
        val token = session.getBearerToken() ?: return forceLogout()
        binding.buttonComplete.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = repository.completeJob(token, jobId)
            binding.progressBar.visibility = View.GONE
            result
                .onSuccess { outcome ->
                    showMessage(getString(if (outcome == SyncOutcome.QUEUED) R.string.saved_offline_will_sync else R.string.job_marked_complete))
                    loadJobDetails()
                }
                .onFailure { e ->
                    Log.w(TAG, "Failed to complete job: ${e.message}")
                    if (e is UnauthorizedException) forceLogout() else showMessage(getString(R.string.error_server_generic))
                    binding.buttonComplete.isEnabled = true
                }
        }
    }

    private fun showRateDialog() {
        val dialogBinding = DialogRateJobBinding.inflate(LayoutInflater.from(this))

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.submit) { _, _ ->
                val score = dialogBinding.ratingBar.rating.toInt().coerceIn(1, 5)
                val comment = dialogBinding.inputComment.text?.toString()?.trim()
                submitRating(score, comment)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun submitRating(score: Int, comment: String?) {
        val token = session.getBearerToken() ?: return forceLogout()
        lifecycleScope.launch {
            repository.rateJob(token, jobId, score, comment?.ifBlank { null })
                .onSuccess { outcome ->
                    showMessage(getString(if (outcome == SyncOutcome.QUEUED) R.string.saved_offline_will_sync else R.string.rating_submitted))
                    binding.buttonRate.isEnabled = false
                }
                .onFailure { e ->
                    Log.w(TAG, "Failed to submit rating: ${e.message}")
                    when {
                        e is UnauthorizedException -> forceLogout()
                        e is ApiException && e.code == 409 -> showMessage(getString(R.string.error_already_rated))
                        else -> showMessage(getString(R.string.error_server_generic))
                    }
                }
        }
    }

    private fun openOtherPartyProfile() {
        val job = currentJob ?: return
        val myId = session.getUserId()
        val otherId = if (myId == job.postedById) job.acceptedById else job.postedById
        if (otherId == null) return

        startActivity(Intent(this, ProfileActivity::class.java).putExtra(ProfileActivity.EXTRA_USER_ID, otherId))
    }

    private fun showMessage(message: String) {
        binding.textMessage.text = message
        binding.textMessage.visibility = View.VISIBLE
    }
}
