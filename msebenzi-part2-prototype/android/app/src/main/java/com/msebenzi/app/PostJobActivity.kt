package com.msebenzi.app

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.msebenzi.app.data.CreateJobRequest
import com.msebenzi.app.data.JobRepository
import com.msebenzi.app.data.ServiceCatalog
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.data.UnauthorizedException
import com.msebenzi.app.data.Validators
import com.msebenzi.app.databinding.ActivityPostJobBinding
import com.msebenzi.app.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

/**
 * Household flow: post a job with a category chosen from the services we offer and an optional
 * photo (uploaded to the API as a BLOB). With no connection the job is saved on the phone and
 * uploaded automatically later (offline sync).
 */
class PostJobActivity : BaseActivity() {

    private lateinit var binding: ActivityPostJobBinding
    private lateinit var session: SessionManager
    private lateinit var repository: JobRepository

    private var selectedCategoryKey: String? = null
    private var photoBytes: ByteArray? = null

    /** Android's system photo picker - needs no storage permission. */
    private val pickPhoto =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    val bytes = ImageUtils.readAsCompressedJpeg(this@PostJobActivity, uri)
                    if (bytes == null) {
                        Toast.makeText(this@PostJobActivity, R.string.error_image_read, Toast.LENGTH_SHORT).show()
                    } else {
                        photoBytes = bytes
                        binding.imagePreview.setImageURI(uri)
                        binding.imagePreview.visibility = View.VISIBLE
                        binding.buttonAddPhoto.setText(R.string.change_photo)
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPostJobBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.headerSection, top = true)
        applyInsets(binding.root, bottom = true, sides = true)

        session = SessionManager(this)
        repository = JobRepository(this)

        binding.buttonBack.setOnClickListener { finish() }

        val names = ServiceCatalog.all.map { getString(it.nameRes) }
        binding.inputCategory.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names))
        binding.inputCategory.setOnItemClickListener { _, _, position, _ ->
            selectedCategoryKey = ServiceCatalog.all[position].key
        }

        binding.buttonAddPhoto.setOnClickListener {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.buttonSubmit.setOnClickListener { submit() }
    }

    private fun submit() {
        val title = binding.inputTitle.text.toString().trim()
        val location = binding.inputLocation.text.toString().trim()
        val description = binding.inputDescription.text.toString().trim()
        val category = selectedCategoryKey.orEmpty()
        val budget = Validators.parseBudget(binding.inputBudget.text.toString())

        if (!Validators.areJobFieldsValid(title, category, location, description)) {
            showError(getString(R.string.error_job_fields))
            return
        }
        if (budget == null) {
            showError(getString(R.string.error_budget_invalid))
            return
        }
        val token = session.getBearerToken() ?: return forceLogout()

        setLoading(true)
        lifecycleScope.launch {
            val result = repository.createJob(
                token = token,
                request = CreateJobRequest(title, description, category, location, budget),
                userId = session.getUserId(),
                photoBytes = photoBytes,
                photoDir = File(filesDir, "pending_photos")
            )
            setLoading(false)
            result
                .onSuccess { job ->
                    Toast.makeText(
                        this@PostJobActivity,
                        if (job.isPendingSync) R.string.job_saved_offline else R.string.job_posted,
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
                .onFailure { e ->
                    if (e is UnauthorizedException) forceLogout() else showError(getString(R.string.error_server_generic))
                }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.buttonSubmit.isEnabled = !loading
        if (loading) binding.textError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }
}
