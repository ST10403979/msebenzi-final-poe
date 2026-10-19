package com.msebenzi.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.msebenzi.app.adapters.JobAdapter
import com.msebenzi.app.adapters.ServiceAdapter
import com.msebenzi.app.data.Job
import com.msebenzi.app.data.JobRepository
import com.msebenzi.app.data.NetworkUtils
import com.msebenzi.app.data.ServiceCatalog
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.data.UnauthorizedException
import com.msebenzi.app.databinding.ActivityMainBinding
import com.msebenzi.app.notify.NotificationHelper
import com.msebenzi.app.notify.NotificationPoller
import com.msebenzi.app.notify.PushRegistrar
import com.msebenzi.app.util.LocaleHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Home screen: greeting, search, the gallery of services we offer, and the live job feed.
 * The feed is offline-first (served from Room when there is no signal) and a banner explains
 * when the user is offline or when changes are waiting to sync.
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var session: SessionManager
    private lateinit var repository: JobRepository
    private lateinit var jobAdapter: JobAdapter

    private var allJobs: List<Job> = emptyList()
    private var selectedService: String? = null
    private var searchQuery: String = ""

    /** Android 13+ runtime permission needed to show notifications. */
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) NotificationHelper.createChannel(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = try {
            SessionManager(this)
        } catch (e: Exception) {
            deleteSharedPreferences("msebenzi_secure_prefs")
            goToLogin()
            return
        }
        if (!session.isLoggedIn()) {
            goToLogin()
            return
        }

        // Make sure the language chosen at registration / in Settings is what we are showing.
        LocaleHelper.apply(session.getLanguage())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.headerSection, top = true)
        applyInsets(binding.bottomNav, bottom = true)

        repository = JobRepository(this)

        setUpHeader()
        setUpLists()
        setUpNavigation()

        binding.swipeRefresh.setColorSchemeResources(R.color.msebenzi_green)
        binding.swipeRefresh.setOnRefreshListener { loadJobs() }

        askForNotificationPermission()
        PushRegistrar.registerCurrentToken(this)
    }

    override fun onResume() {
        super.onResume()
        if (::session.isInitialized && session.isLoggedIn()) loadJobs()
    }

    override fun onStart() {
        super.onStart()
        if (!::session.isInitialized || !session.isLoggedIn()) return
        // REAL-TIME ALERTS while the app is open: check for new targeted alerts every 20 seconds.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val unread = NotificationPoller.checkForNewAlerts(this@MainActivity)
                    if (unread >= 0) updateAlertsBadge(unread)
                    delay(20_000)
                }
            }
        }
    }

    // ---------------------------------------------------------------------- setup

    private fun setUpHeader() {
        val firstName = session.getName().substringBefore(" ").ifBlank { getString(R.string.app_name) }
        binding.textGreeting.text = getString(R.string.greeting, firstName)
        binding.textSubtitle.setText(
            if (session.isWorker()) R.string.subtitle_worker else R.string.subtitle_household
        )
        // Only households post jobs; workers browse and accept them.
        binding.fabPostJob.visibility = if (session.isWorker()) View.GONE else View.VISIBLE
        binding.fabPostJob.setOnClickListener {
            startActivity(Intent(this, PostJobActivity::class.java))
        }
        binding.inputSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim().orEmpty()
                applyFilters()
            }
        })
    }

    private fun setUpLists() {
        binding.recyclerServices.adapter = ServiceAdapter { key ->
            selectedService = key
            applyFilters()
        }

        jobAdapter = JobAdapter { job ->
            startActivity(
                Intent(this, JobDetailActivity::class.java).putExtra(JobDetailActivity.EXTRA_JOB_ID, job.id)
            )
        }
        binding.recyclerJobs.layoutManager = LinearLayoutManager(this)
        binding.recyclerJobs.adapter = jobAdapter
    }

    private fun setUpNavigation() {
        // Returning false for the other tabs keeps "Home" highlighted when we come back.
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_alerts -> {
                    startActivity(Intent(this, AlertsActivity::class.java))
                    false
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    false
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    false
                }
                else -> true
            }
        }
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !NotificationHelper.hasPermission(this) &&
            !session.hasAskedNotificationPermission()
        ) {
            session.setAskedNotificationPermission()
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ----------------------------------------------------------------------- data

    private fun loadJobs() {
        val token = session.getBearerToken() ?: return goToLogin()
        binding.swipeRefresh.isRefreshing = true
        lifecycleScope.launch {
            val result = repository.getJobFeed(token)
            binding.swipeRefresh.isRefreshing = false
            result
                .onSuccess { jobs ->
                    allJobs = jobs
                    applyFilters()
                }
                .onFailure { e ->
                    if (e is UnauthorizedException) {
                        forceLogout()
                    } else {
                        Toast.makeText(this@MainActivity, R.string.error_network, Toast.LENGTH_SHORT).show()
                    }
                }
            updateOfflineBanner()
        }
    }

    private fun applyFilters() {
        val query = searchQuery.lowercase(Locale.getDefault())
        val filtered = allJobs.filter { job ->
            val matchesService = selectedService == null || job.category.equals(selectedService, ignoreCase = true)
            val serviceName = ServiceCatalog.find(job.category)?.let { getString(it.nameRes) } ?: job.category
            val matchesQuery = query.isEmpty() ||
                job.title.lowercase(Locale.getDefault()).contains(query) ||
                job.location.lowercase(Locale.getDefault()).contains(query) ||
                job.description.lowercase(Locale.getDefault()).contains(query) ||
                serviceName.lowercase(Locale.getDefault()).contains(query)
            matchesService && matchesQuery
        }
        jobAdapter.submitList(filtered)
        binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.textJobsTitle.text = if (selectedService != null) {
            getString(R.string.jobs_in_service, getString(ServiceCatalog.find(selectedService)?.nameRes ?: R.string.latest_jobs))
        } else {
            getString(R.string.latest_jobs)
        }
    }

    private fun updateOfflineBanner() {
        lifecycleScope.launch {
            val online = NetworkUtils.isOnline(this@MainActivity)
            val pending = repository.pendingCount()
            val text = when {
                !online && pending > 0 -> getString(R.string.banner_offline_pending, pending)
                !online -> getString(R.string.banner_offline)
                pending > 0 -> getString(R.string.banner_pending, pending)
                else -> null
            }
            binding.bannerOffline.visibility = if (text == null) View.GONE else View.VISIBLE
            binding.bannerOffline.text = text.orEmpty()
        }
    }

    private fun updateAlertsBadge(unread: Int) {
        if (unread > 0) {
            val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_alerts)
            badge.isVisible = true
            badge.number = unread
        } else {
            binding.bottomNav.removeBadge(R.id.nav_alerts)
        }
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
