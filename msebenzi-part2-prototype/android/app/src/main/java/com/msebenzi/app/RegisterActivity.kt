package com.msebenzi.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.msebenzi.app.data.LoginRequest
import com.msebenzi.app.data.RegisterRequest
import com.msebenzi.app.data.RetrofitClient
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.data.Validators
import com.msebenzi.app.databinding.ActivityRegisterBinding
import com.msebenzi.app.notify.PushRegistrar
import com.msebenzi.app.util.LocaleHelper
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Registration with input validation. The password is sent over HTTPS and hashed with bcrypt
 * on the server - it is never stored in plain text anywhere.
 */
class RegisterActivity : BaseActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.headerSection, top = true)
        applyInsets(binding.root, bottom = true, sides = true)

        session = SessionManager(this)

        binding.buttonBack.setOnClickListener { finish() }
        binding.buttonRegister.setOnClickListener { attemptRegister() }
        binding.toggleRole.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                binding.textRoleHint.setText(
                    if (checkedId == R.id.buttonRoleWorker) R.string.role_hint_worker else R.string.role_hint_household
                )
            }
        }
    }

    private fun attemptRegister() {
        val name = binding.inputName.text.toString().trim()
        val email = binding.inputEmail.text.toString().trim()
        val password = binding.inputPassword.text.toString()
        val role = if (binding.toggleRole.checkedButtonId == R.id.buttonRoleWorker) "worker" else "household"

        if (!Validators.isValidName(name)) {
            showError(getString(R.string.error_name_required))
            return
        }
        if (!Validators.isValidEmail(email)) {
            showError(getString(R.string.error_invalid_email))
            return
        }
        if (!Validators.isValidPassword(password)) {
            showError(getString(R.string.error_password_short))
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.api
                val response = api.register(RegisterRequest(name, email, password, role, LocaleHelper.deviceLanguage()))
                if (response.code() == 409) {
                    setLoading(false)
                    showError(getString(R.string.error_email_taken))
                    return@launch
                }
                if (!response.isSuccessful) {
                    setLoading(false)
                    showError(getString(R.string.error_server_generic))
                    return@launch
                }

                // The API returns a token on register; if an older server doesn't, log in instead.
                var token = response.body()?.token
                var user = response.body()?.user
                if (token == null || user == null) {
                    val login = api.login(LoginRequest(email, password))
                    token = login.body()?.token
                    user = login.body()?.user
                }
                if (token != null && user != null) {
                    session.saveSession(token, user)
                    PushRegistrar.registerCurrentToken(this@RegisterActivity)
                    startActivity(
                        Intent(this@RegisterActivity, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
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

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.buttonRegister.isEnabled = !loading
        if (loading) binding.textError.visibility = View.GONE
    }

    private fun showError(message: String) {
        binding.textError.text = message
        binding.textError.visibility = View.VISIBLE
    }
}
