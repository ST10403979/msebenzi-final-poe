package com.msebenzi.app.util

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.msebenzi.app.BuildConfig

/**
 * Single sign-on (SSO) with Google using Android's Credential Manager. The returned Google
 * ID token is sent to our API, which verifies it with Google and issues the app's own JWT.
 *
 * Needs an OAuth *web* client ID (see docs/SETUP_GUIDE.md section 2), supplied through
 * `google.web.client.id` in local.properties / the GOOGLE_WEB_CLIENT_ID environment variable.
 *
 * Reference: Android Developers (2026) Sign in your user with Credential Manager.
 */
object GoogleSignInHelper {

    fun isConfigured(): Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /**
     * Shows the Google account chooser and returns the Google ID token.
     * Throws androidx.credentials.exceptions.GetCredentialException if the user cancels or
     * has no Google account on the device.
     */
    suspend fun getIdToken(activity: Activity): String {
        val option = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(activity).getCredential(activity, request)

        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        throw IllegalStateException("Unexpected credential type")
    }
}
