package com.msebenzi.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the JWT and basic user info using Android's EncryptedSharedPreferences,
 * so the token never sits on disk in plain text (AES-256 encrypted keys and values,
 * master key kept in the Android Keystore).
 *
 * Reference: Android Developers (2026) Work with data more securely - Security library.
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences

    init {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            "msebenzi_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(token: String, user: User) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putInt(KEY_USER_ID, user.id)
            .putString(KEY_NAME, user.name)
            .putString(KEY_EMAIL, user.email)
            .putString(KEY_ROLE, user.role)
            .putString(KEY_LANGUAGE, user.language ?: "en")
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    /** Convenience for Retrofit's Authorization header. */
    fun getBearerToken(): String? = getToken()?.let { "Bearer $it" }

    fun getUserId(): Int = prefs.getInt(KEY_USER_ID, -1)

    fun getName(): String = prefs.getString(KEY_NAME, "") ?: ""

    fun setName(name: String) {
        prefs.edit().putString(KEY_NAME, name).apply()
    }

    fun getEmail(): String = prefs.getString(KEY_EMAIL, "") ?: ""

    fun getUserRole(): String? = prefs.getString(KEY_ROLE, null)

    fun isWorker(): Boolean = getUserRole() == "worker"

    fun getLanguage(): String = prefs.getString(KEY_LANGUAGE, "en") ?: "en"

    fun setLanguage(lang: String) {
        prefs.edit().putString(KEY_LANGUAGE, lang).apply()
    }

    // ---- Notification preferences ----

    fun notificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS, true)

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS, enabled).apply()
    }

    /** Highest alert id we have already shown as a system notification (-1 = never polled). */
    fun getLastNotifiedId(): Int = prefs.getInt(KEY_LAST_NOTIFIED, -1)

    fun setLastNotifiedId(id: Int) {
        prefs.edit().putInt(KEY_LAST_NOTIFIED, id).apply()
    }

    /** True once the user has been asked for the Android 13+ notification permission. */
    fun hasAskedNotificationPermission(): Boolean = prefs.getBoolean(KEY_ASKED_PERMISSION, false)

    fun setAskedNotificationPermission() {
        prefs.edit().putBoolean(KEY_ASKED_PERMISSION, true).apply()
    }

    fun isLoggedIn(): Boolean = getToken() != null

    /** Logs out: removes the account data but keeps device preferences (language, notifications). */
    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_NAME)
            .remove(KEY_EMAIL)
            .remove(KEY_ROLE)
            .remove(KEY_LAST_NOTIFIED)
            .apply()
    }

    companion object {
        private const val KEY_TOKEN = "token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_NAME = "name"
        private const val KEY_EMAIL = "email"
        private const val KEY_ROLE = "role"
        private const val KEY_LANGUAGE = "language"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_LAST_NOTIFIED = "last_notified_id"
        private const val KEY_ASKED_PERMISSION = "asked_notification_permission"
    }
}
