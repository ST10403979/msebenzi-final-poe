package com.msebenzi.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.msebenzi.app.data.AppDatabase
import com.msebenzi.app.data.JobRepository
import com.msebenzi.app.data.NetworkUtils
import com.msebenzi.app.data.RetrofitClient
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.data.UpdateProfileRequest
import com.msebenzi.app.databinding.ActivitySettingsBinding
import com.msebenzi.app.notify.NotificationHelper
import com.msebenzi.app.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings: language (English / Afrikaans / isiXhosa, applied instantly), notifications,
 * manual offline-sync, privacy policy, log out and account deletion (required by Google Play
 * for apps that let people create an account).
 */
class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var session: SessionManager
    private lateinit var repository: JobRepository

    companion object {
        private const val TAG = "SettingsActivity"
    }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                binding.switchNotifications.isChecked = false
                showMessage(getString(R.string.notifications_denied))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.headerSection, top = true)
        applyInsets(binding.root, bottom = true, sides = true)

        session = SessionManager(this)
        repository = JobRepository(this)

        binding.textAccount.text = session.getEmail()
        binding.textVersion.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)

        // Current values
        when (session.getLanguage()) {
            "af" -> binding.radioAfrikaans.isChecked = true
            "xh" -> binding.radioXhosa.isChecked = true
            else -> binding.radioEnglish.isChecked = true
        }
        binding.switchNotifications.isChecked = session.notificationsEnabled()

        binding.buttonBack.setOnClickListener { finish() }
        binding.switchNotifications.setOnCheckedChangeListener { _, checked ->
            if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !NotificationHelper.hasPermission(this)) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        binding.buttonSave.setOnClickListener { saveSettings() }
        binding.buttonSyncNow.setOnClickListener { syncNow() }
        binding.buttonPrivacy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.API_BASE_URL}privacy")))
        }
        binding.buttonLogout.setOnClickListener { logout() }
        binding.buttonDeleteAccount.setOnClickListener { confirmDeleteAccount() }

        refreshSyncStatus()
    }

    // ----------------------------------------------------------------- settings

    private fun selectedLanguage(): String = when (binding.radioLanguage.checkedRadioButtonId) {
        R.id.radioAfrikaans -> "af"
        R.id.radioXhosa -> "xh"
        else -> "en"
    }

    private fun saveSettings() {
        val language = selectedLanguage()
        val token = session.getBearerToken() ?: return forceLogout()

        session.setNotificationsEnabled(binding.switchNotifications.isChecked)
        val languageChanged = language != session.getLanguage()
        session.setLanguage(language)

        binding.buttonSave.isEnabled = false
        lifecycleScope.launch {
            // Save on the server too, so the language follows the account to a new phone.
            try {
                RetrofitClient.api.updateProfile(token, UpdateProfileRequest(language = language))
            } catch (e: Exception) {
                Log.w(TAG, "Language saved on device only (offline): ${e.message}")
            }
            binding.buttonSave.isEnabled = true
            showMessage(getString(R.string.settings_saved))
            // Applying the locale recreates the visible screens in the new language.
            if (languageChanged) LocaleHelper.apply(language)
        }
    }

    private fun syncNow() {
        val token = session.getBearerToken() ?: return forceLogout()
        binding.buttonSyncNow.isEnabled = false
        lifecycleScope.launch {
            if (!NetworkUtils.isOnline(this@SettingsActivity)) {
                showMessage(getString(R.string.sync_no_connection))
            } else {
                val count = repository.flushPending(token)
                showMessage(getString(R.string.sync_done, count))
            }
            binding.buttonSyncNow.isEnabled = true
            refreshSyncStatus()
        }
    }

    private fun refreshSyncStatus() {
        lifecycleScope.launch {
            val pending = repository.pendingCount()
            binding.textSyncStatus.text =
                if (pending == 0) getString(R.string.sync_all_synced) else getString(R.string.sync_pending_count, pending)
        }
    }

    // ------------------------------------------------------------------ account

    private fun logout() {
        lifecycleScope.launch {
            val token = session.getBearerToken()
            if (token != null) {
                try {
                    RetrofitClient.api.clearFcmToken(token) // stop pushes to this phone
                } catch (e: Exception) {
                    Log.w(TAG, "Could not clear push token: ${e.message}")
                }
            }
            finishSession()
        }
    }

    private fun confirmDeleteAccount() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_account)
            .setMessage(R.string.delete_account_message)
            .setPositiveButton(R.string.delete_account_confirm) { _, _ -> deleteAccount() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteAccount() {
        val token = session.getBearerToken() ?: return forceLogout()
        binding.buttonDeleteAccount.isEnabled = false
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.deleteAccount(token)
                if (response.isSuccessful) {
                    finishSession()
                } else {
                    binding.buttonDeleteAccount.isEnabled = true
                    showMessage(getString(R.string.error_server_generic))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Delete account failed: ${e.message}")
                binding.buttonDeleteAccount.isEnabled = true
                showMessage(getString(R.string.error_network))
            }
        }
    }

    /** Clears the session and everything cached for this user, then returns to the login screen. */
    private suspend fun finishSession() {
        session.clear()
        withContext(Dispatchers.IO) {
            AppDatabase.getInstance(applicationContext).clearAllTables()
            filesDir.resolve("pending_photos").deleteRecursively()
        }
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun showMessage(message: String) {
        binding.textMessage.text = message
        binding.textMessage.visibility = View.VISIBLE
    }
}
