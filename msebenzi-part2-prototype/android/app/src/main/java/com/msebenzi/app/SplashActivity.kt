package com.msebenzi.app

import android.content.Intent
import android.os.Bundle
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.ScaleAnimation
import androidx.lifecycle.lifecycleScope
import com.msebenzi.app.data.SessionManager
import com.msebenzi.app.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Branded launch screen. Routes to Home if a saved session exists, otherwise to Login. */
class SplashActivity : BaseActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Gentle fade + grow animation on the logo.
        val animation = AnimationSet(true).apply {
            addAnimation(AlphaAnimation(0f, 1f).apply { duration = 700 })
            addAnimation(
                ScaleAnimation(
                    0.8f, 1f, 0.8f, 1f,
                    ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f
                ).apply { duration = 700 }
            )
        }
        binding.imageLogo.startAnimation(animation)

        lifecycleScope.launch {
            delay(1300)
            val loggedIn = try {
                SessionManager(this@SplashActivity).isLoggedIn()
            } catch (e: Exception) {
                // Encrypted prefs can become unreadable (e.g. restored on another device): start fresh.
                deleteSharedPreferences("msebenzi_secure_prefs")
                false
            }
            val next = if (loggedIn) MainActivity::class.java else LoginActivity::class.java
            startActivity(Intent(this@SplashActivity, next))
            finish()
        }
    }
}
