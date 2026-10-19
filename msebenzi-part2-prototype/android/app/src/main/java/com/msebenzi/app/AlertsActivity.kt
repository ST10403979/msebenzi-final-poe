package com.msebenzi.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.msebenzi.app.adapters.NotificationAdapter
import com.msebenzi.app.data.RetrofitClient
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.databinding.ActivityAlertsBinding
import kotlinx.coroutines.launch

/**
 * Feature: Targeted job alerts. Shows the notifications the backend generated for this
 * specific user (see backend/utils/notify.js) - new jobs matching a worker's skills, and
 * job-status updates for both roles. The same alerts are also delivered as system push
 * notifications (see notify/ package).
 */
class AlertsActivity : BaseActivity() {

    private lateinit var binding: ActivityAlertsBinding
    private lateinit var session: SessionManager
    private lateinit var adapter: NotificationAdapter

    companion object {
        private const val TAG = "AlertsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAlertsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.headerSection, top = true)
        applyInsets(binding.listContainer, bottom = true, sides = true)

        session = SessionManager(this)
        binding.buttonBack.setOnClickListener { finish() }

        adapter = NotificationAdapter { notification ->
            markRead(notification.id)
            val jobId = notification.jobId
            if (jobId != null) {
                startActivity(Intent(this, JobDetailActivity::class.java).putExtra(JobDetailActivity.EXTRA_JOB_ID, jobId))
            }
        }
        binding.recyclerNotifications.layoutManager = LinearLayoutManager(this)
        binding.recyclerNotifications.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        loadNotifications()
    }

    private fun loadNotifications() {
        val token = session.getBearerToken() ?: return forceLogout()
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.getNotifications(token)
                val notifications = response.body()
                if (response.isSuccessful && notifications != null) {
                    adapter.submitList(notifications)
                    binding.textEmpty.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
                } else if (response.code() == 401) {
                    forceLogout()
                } else {
                    Toast.makeText(this@AlertsActivity, R.string.error_server_generic, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load notifications: ${e.message}")
                Toast.makeText(this@AlertsActivity, R.string.error_network, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun markRead(id: Int) {
        val token = session.getBearerToken() ?: return
        lifecycleScope.launch {
            try {
                RetrofitClient.api.markNotificationRead(token, id)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to mark notification read: ${e.message}")
            }
        }
    }
}
