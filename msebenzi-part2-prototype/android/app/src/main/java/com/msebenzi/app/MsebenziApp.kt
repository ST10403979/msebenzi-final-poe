package com.msebenzi.app

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.msebenzi.app.data.RetrofitClient
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.data.SyncWorker
import com.msebenzi.app.notify.NotificationHelper
import java.util.concurrent.TimeUnit

/**
 * Application entry point: sets up the notification channel, background sync and the image
 * loader that downloads stored photos (BLOBs) from the API with the user's login token.
 */
class MsebenziApp : Application(), ImageLoaderFactory {

    private val session by lazy { SessionManager(this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannel(this)
        schedulePeriodicSync()
        syncWhenConnectivityReturns()
    }

    private val connectedConstraints: Constraints
        get() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /**
     * Schedules SyncWorker every 15 minutes (the minimum WorkManager allows) while the device
     * has connectivity, so queued offline actions are pushed and new alerts are fetched even
     * if the app is closed.
     */
    private fun schedulePeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connectedConstraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "msebenzi_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    /**
     * OFFLINE SYNC: the moment the phone gets a connection back, run the sync immediately
     * instead of waiting for the next 15-minute slot.
     */
    private fun syncWhenConnectivityReturns() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        try {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d("MsebenziApp", "Network available - starting sync")
                    WorkManager.getInstance(this@MsebenziApp).enqueueUniqueWork(
                        "msebenzi_sync_now",
                        ExistingWorkPolicy.KEEP,
                        OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(connectedConstraints).build()
                    )
                }
            })
        } catch (e: Exception) {
            Log.w("MsebenziApp", "Could not register connectivity callback: ${e.message}")
        }
    }

    /** Coil image loader: adds the Authorization header so protected /api/media images load. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            RetrofitClient.okHttpClient.newBuilder()
                .addInterceptor { chain ->
                    val token = try {
                        session.getBearerToken()
                    } catch (e: Exception) {
                        null
                    }
                    val request = if (token != null) {
                        chain.request().newBuilder().header("Authorization", token).build()
                    } else {
                        chain.request()
                    }
                    chain.proceed(request)
                }
                .build()
        }
        .crossfade(true)
        .build()
}
