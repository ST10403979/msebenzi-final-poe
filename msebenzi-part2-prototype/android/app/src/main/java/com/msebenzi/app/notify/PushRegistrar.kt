package com.msebenzi.app.notify

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.msebenzi.app.data.FcmTokenRequest
import com.msebenzi.app.data.RetrofitClient
import com.msebenzi.app.data.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Registers this device's Firebase Cloud Messaging token with the API so the server can send
 * real-time push notifications to it. If Firebase is not configured for this build (no
 * google-services.json) every call quietly does nothing and the app uses polling instead.
 *
 * Reference: Firebase (2026) Set up a Firebase Cloud Messaging client app on Android.
 */
object PushRegistrar {

    private const val TAG = "PushRegistrar"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fetches the current FCM token and uploads it. Call after login and on app start. */
    fun registerCurrentToken(context: Context) {
        val appContext = context.applicationContext
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful && task.result != null) {
                    sendToServer(appContext, task.result)
                } else {
                    Log.d(TAG, "FCM token unavailable: ${task.exception?.message}")
                }
            }
        } catch (e: Exception) {
            // IllegalStateException when Firebase has not been initialised (no google-services.json).
            Log.i(TAG, "Firebase not configured - using in-app alert polling instead.")
        }
    }

    /** Called by the messaging service when Firebase rotates the token. */
    fun onNewToken(context: Context, token: String) = sendToServer(context.applicationContext, token)

    private fun sendToServer(context: Context, fcmToken: String) {
        val bearer = SessionManager(context).getBearerToken() ?: return
        scope.launch {
            try {
                val response = RetrofitClient.api.registerFcmToken(bearer, FcmTokenRequest(fcmToken))
                Log.i(TAG, "FCM token registered (HTTP ${response.code()})")
            } catch (e: Exception) {
                Log.w(TAG, "Could not register FCM token: ${e.message}")
            }
        }
    }
}
