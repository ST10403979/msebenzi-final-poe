package com.msebenzi.app

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.msebenzi.app.adapters.RatingAdapter
import com.msebenzi.app.data.JobRepository
import com.msebenzi.app.data.RetrofitClient
import com.msebenzi.app.data.ServiceCatalog
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.data.UpdateProfileRequest
import com.msebenzi.app.data.User
import com.msebenzi.app.databinding.ActivityProfileBinding
import com.msebenzi.app.util.ImageUtils
import com.msebenzi.app.util.ImageUtils.loadMedia
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Feature: Worker profile and verification (+ trusted ratings and reviews).
 * Shows a profile photo (stored as a BLOB on the API), skills, bio, reputation and the
 * verification badge. On your own profile you can change the photo, pick your skills
 * (these drive targeted job alerts), edit your bio and request verification.
 */
class ProfileActivity : BaseActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var session: SessionManager
    private lateinit var ratingAdapter: RatingAdapter
    private lateinit var repository: JobRepository

    private var viewedUserId: Int = -1
    private var isOwnProfile = true
    private var currentUser: User? = null

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        private const val TAG = "ProfileActivity"
    }

    private val pickAvatar =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadAvatar(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.headerSection, top = true)
        applyInsets(binding.root, bottom = true, sides = true)

        session = SessionManager(this)
        repository = JobRepository(this)

        val requestedId = intent.getIntExtra(EXTRA_USER_ID, -1)
        isOwnProfile = requestedId == -1 || requestedId == session.getUserId()
        viewedUserId = if (isOwnProfile) session.getUserId() else requestedId

        binding.buttonBack.setOnClickListener { finish() }

        ratingAdapter = RatingAdapter(emptyList())
        binding.recyclerRatings.layoutManager = LinearLayoutManager(this)
        binding.recyclerRatings.adapter = ratingAdapter

        binding.buttonChangePhoto.setOnClickListener {
            pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.buttonSaveProfile.setOnClickListener { saveProfile() }
        binding.buttonRequestVerification.setOnClickListener { requestVerification() }

        loadProfile()
        loadRatings()
    }

    // ------------------------------------------------------------------- loading

    private fun loadProfile() {
        val token = session.getBearerToken() ?: return forceLogout()
        lifecycleScope.launch {
            try {
                val response = if (isOwnProfile) {
                    RetrofitClient.api.getProfile(token)
                } else {
                    RetrofitClient.api.getUserProfile(token, viewedUserId)
                }
                val user = response.body()
                if (response.isSuccessful && user != null) {
                    currentUser = user
                    bindProfile(user)
                } else if (response.code() == 401) {
                    forceLogout()
                } else {
                    showMessage(getString(R.string.error_server_generic))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load profile: ${e.message}")
                showMessage(getString(R.string.error_network))
            }
        }
    }

    private fun bindProfile(user: User) {
        val isWorker = user.role == "worker"
        val verified = user.verificationStatus == "verified"

        binding.textName.text = user.name
        binding.imageAvatar.loadMedia(user.avatarMediaId, R.drawable.ic_avatar_placeholder)
        binding.imageVerified.visibility = if (verified) View.VISIBLE else View.GONE
        binding.textVerifiedBadge.visibility = if (verified) View.VISIBLE else View.GONE

        val roleText = getString(if (isWorker) R.string.worker else R.string.household)
        binding.textRoleAndRating.text = if (user.ratingCount > 0) {
            getString(
                R.string.role_rating_summary, roleText,
                String.format(Locale.getDefault(), "%.1f", user.ratingAverage),
                resources.getQuantityString(R.plurals.review_count, user.ratingCount, user.ratingCount)
            )
        } else {
            roleText
        }

        val skillNames = skillKeys(user.skills).map { key ->
            ServiceCatalog.find(key)?.let { getString(it.nameRes) } ?: key
        }
        binding.textSkills.text = if (skillNames.isEmpty()) "—" else skillNames.joinToString(", ")
        binding.textBio.text = user.bio?.takeIf { it.isNotBlank() } ?: "—"

        if (isOwnProfile) {
            binding.buttonChangePhoto.visibility = View.VISIBLE
            binding.editSection.visibility = View.VISIBLE
            binding.inputBio.setText(user.bio.orEmpty())
            buildSkillChips(skillKeys(user.skills), isWorker)
            binding.buttonRequestVerification.visibility = if (isWorker) View.VISIBLE else View.GONE
            when (user.verificationStatus) {
                "verified" -> {
                    binding.buttonRequestVerification.setText(R.string.verified)
                    binding.buttonRequestVerification.isEnabled = false
                }
                "pending" -> {
                    binding.buttonRequestVerification.setText(R.string.verification_pending)
                    binding.buttonRequestVerification.isEnabled = false
                }
                else -> {
                    binding.buttonRequestVerification.setText(R.string.request_verification)
                    binding.buttonRequestVerification.isEnabled = true
                }
            }
        }
    }

    /** "cleaning, Gardening" -> ["cleaning", "gardening"] */
    private fun skillKeys(skills: String?): List<String> =
        skills.orEmpty().split(",").map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }

    private fun buildSkillChips(selected: List<String>, isWorker: Boolean) {
        val group = binding.chipGroupSkills
        group.removeAllViews()
        // Households don't have skills - only workers pick services to be alerted about.
        group.visibility = if (isWorker) View.VISIBLE else View.GONE
        if (!isWorker) return
        ServiceCatalog.all.forEach { service ->
            val chip = Chip(this).apply {
                text = getString(service.nameRes)
                tag = service.key
                isCheckable = true
                isChecked = service.key in selected
            }
            group.addView(chip)
        }
    }

    private fun loadRatings() {
        val token = session.getBearerToken() ?: return
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getRatingsForUser(token, viewedUserId)
                val ratings = response.body()
                if (response.isSuccessful && ratings != null) {
                    ratingAdapter.updateData(ratings)
                    binding.textNoRatings.visibility = if (ratings.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load ratings: ${e.message}")
            }
        }
    }

    // ------------------------------------------------------------------- editing

    private fun saveProfile() {
        val token = session.getBearerToken() ?: return forceLogout()
        val bio = binding.inputBio.text?.toString()?.trim().orEmpty()
        val isWorker = currentUser?.role == "worker"
        val skills = if (isWorker) {
            (0 until binding.chipGroupSkills.childCount)
                .map { binding.chipGroupSkills.getChildAt(it) as Chip }
                .filter { it.isChecked }
                .joinToString(",") { it.tag as String }
        } else {
            null
        }

        binding.buttonSaveProfile.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.updateProfile(token, UpdateProfileRequest(skills = skills, bio = bio))
                if (response.isSuccessful) {
                    showMessage(getString(R.string.profile_saved))
                    loadProfile()
                } else {
                    showMessage(getString(R.string.error_server_generic))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save profile: ${e.message}")
                showMessage(getString(R.string.error_network))
            }
            binding.buttonSaveProfile.isEnabled = true
        }
    }

    /** Blob storage: compress the picked photo, upload it, then point the profile at it. */
    private fun uploadAvatar(uri: android.net.Uri) {
        val token = session.getBearerToken() ?: return forceLogout()
        binding.buttonChangePhoto.isEnabled = false
        lifecycleScope.launch {
            val bytes = ImageUtils.readAsCompressedJpeg(this@ProfileActivity, uri, maxSide = 800)
            if (bytes == null) {
                showMessage(getString(R.string.error_image_read))
            } else {
                repository.uploadMedia(token, bytes)
                    .onSuccess { mediaId ->
                        try {
                            val response = RetrofitClient.api.updateProfile(token, UpdateProfileRequest(avatarMediaId = mediaId))
                            if (response.isSuccessful) {
                                showMessage(getString(R.string.photo_updated))
                                loadProfile()
                            } else {
                                showMessage(getString(R.string.error_server_generic))
                            }
                        } catch (e: Exception) {
                            showMessage(getString(R.string.error_network))
                        }
                    }
                    .onFailure { e ->
                        Log.w(TAG, "Avatar upload failed: ${e.message}")
                        showMessage(getString(R.string.error_network))
                    }
            }
            binding.buttonChangePhoto.isEnabled = true
        }
    }

    private fun requestVerification() {
        val token = session.getBearerToken() ?: return forceLogout()
        binding.buttonRequestVerification.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.requestVerification(token)
                if (response.isSuccessful) {
                    binding.buttonRequestVerification.setText(R.string.verification_pending)
                    showMessage(getString(R.string.verification_requested))
                } else {
                    showMessage(getString(R.string.error_server_generic))
                    binding.buttonRequestVerification.isEnabled = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to request verification: ${e.message}")
                showMessage(getString(R.string.error_network))
                binding.buttonRequestVerification.isEnabled = true
            }
        }
    }

    private fun showMessage(message: String) {
        binding.textMessage.text = message
        binding.textMessage.visibility = View.VISIBLE
    }
}
