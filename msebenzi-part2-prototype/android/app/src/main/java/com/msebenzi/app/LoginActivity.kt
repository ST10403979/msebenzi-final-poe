package com.msebenzi.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.msebenzi.app.data.GoogleLoginRequest
import com.msebenzi.app.data.LoginRequest
import com.msebenzi.app.data.RetrofitClient
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.data.User
import com.msebenzi.app.data.Validators
import com.msebenzi.app.databinding.ActivityLoginBinding
import com.msebenzi.app.notify.PushRegistrar
import com.msebenzi.app.util.GoogleSignInHelper
import com.msebenzi.app.util.LocaleHelper
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Log in with email + password, or with Google single sign-on (SSO).
 * Passwords are never stored on the phone - only the JWT returned by the API, which is kept
 * in encrypted storage (SessionManager). The API stores only a bcrypt hash of the password.
 */
class LoginActivity : BaseActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.headerSection, top = true)
        applyInsets(binding.root, bottom = true, sides = true)

        session = SessionManager(this)

        binding.buttonLogin.setOnClickListener { attemptLogin() }
        binding.buttonGoogle.setOnClickListener { startGoogleSignIn() }
        binding.buttonGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    // ------------------------------------------------------------- email + password

    private fun attemptLogin() {
        val email = binding.inputEmail.text.toString().trim()
        val password = binding.inputPassword.text.toString()

        if (!Validators.isValidEmail(email)) {
            showError(getString(R.string.error_invalid_email))
            return
        }
        if (!Validators.isNonEmptyPassword(password)) {
            showError(getString(R.string.error_password_required))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = RetrofitClient.api.login(LoginRequest(email, password))
                val body = response.body()
                val token = body?.token
                val user = body?.user
                if (response.isSuccessful && token != null && user != null) {
                    completeLogin(token, user)
                } else if (response.code() == 401) {
                    setLoading(false)
                    showError(getString(R.string.error_invalid_credentials))
                } else {
                    setLoading(false)
                    showError(getString(R.string.error_server_generic))
                }
            } catch (e: IOException) {
                setLoading(false)
                showError(getString(R.string.error_network))
            } catch (e: Exception) {
                setLoading(false)
                showError(getString(R.string.error_server_generic))
            }
        }
    }

    // ------------------------------------------------------------------- Google SSO

    private fun startGoogleSignIn() {
        if (!GoogleSignInHelper.isConfigured()) {
            showError(getString(R.string.google_not_configured))
            return
        }
        setLoading(true)
        lifecycleScope.launch {
            try {
                val idToken = GoogleSignInHelper.getIdToken(this@LoginActivity)
                googleLogin(idToken, null)
            } catch (e: GetCredentialCancellationException) {
                setLoading(false) // user closed the account picker - nothing to report
            } catch (e: NoCredentialException) {
                setLoading(false)
                showError(getString(R.string.error_no_google_account))
            } catch (e: GetCredentialException) {
                setLoading(false)
                showError(getString(R.string.error_google_failed))
            } catch (e: Exception) {
                setLoading(false)
                showError(getString(R.string.error_google_failed))
            }
        }
    }

    /** Sends the Google ID token to our API. A brand-new Google user is first asked for a role. */
    private suspend fun googleLogin(idToken: String, role: String?) {
        try {
            val response = RetrofitClient.api.googleLogin(
                GoogleLoginRequest(idToken, role, LocaleHelper.deviceLanguage())
            )
            val body = response.body()
            val token = body?.token
            val user = body?.user
            when {
                response.isSuccessful && body?.needsRole == true -> {
                    setLoading(false)
                    askRoleThenContinue(idToken)
                }
                response.isSuccessful && token != null && user != null -> completeLogin(token, user)
                else -> {
                    setLoading(false)
                    showError(getString(R.string.error_google_failed))
                }
            }
        } catch (e: IOException) {
            setLoading(false)
            showError(getString(R.string.error_network))
        } catch (e: Exception) {
            setLoading(false)
            showError(getString(R.string.error_google_failed))
        }
    }

    private fun askRoleThenContinue(idToken: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.i_am_a)
            .setItems(arrayOf(getString(R.string.household), getString(R.string.worker))) { _, which ->
                val role = if (which == 0) "household" else "worker"
                setLoading(true)
                lifecycleScope.launch { googleLogin(idToken, role) }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ----------------------------------------------------------------------- shared

    private fun completeLogin(token: String, user: User) {
        session.saveSession(token, user)
        PushRegistrar.registerCurrentToken(this)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.buttonLogin.isEnabled = !loading
        binding.buttonGoogle.isEnabled = !loading
        if (loading) binding.textError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }
}
