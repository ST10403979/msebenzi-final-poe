package com.msebenzi.app

import android.graphics.Color
import android.view.View
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.msebenzi.app.data.SessionManager
import kotlin.math.max

/**
 * Base class for every screen. Apps targeting Android 15+ (we target 16 / API 36 for Google
 * Play) are drawn edge-to-edge, behind the status and navigation bars, so each screen pads its
 * header (top) and footer (bottom) by the system-bar insets - and by the keyboard height - to
 * keep content from being hidden.
 *
 * Reference: Android Developers (2026) Display content edge-to-edge in your app.
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Light icons on our green headers; dark icons on the (light) navigation bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
    }

    /**
     * Adds the system-bar insets to [view]'s existing padding.
     * @param top    add the status-bar height to the top padding
     * @param bottom add the navigation-bar / keyboard height to the bottom padding
     * @param sides  add the left/right insets (landscape cut-outs / gesture bars)
     */
    protected fun applyInsets(view: View, top: Boolean = false, bottom: Boolean = false, sides: Boolean = false) {
        val left = view.paddingLeft
        val topPad = view.paddingTop
        val right = view.paddingRight
        val bottomPad = view.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                if (sides) left + bars.left else left,
                if (top) topPad + bars.top else topPad,
                if (sides) right + bars.right else right,
                if (bottom) bottomPad + max(bars.bottom, ime.bottom) else bottomPad
            )
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    /** Called when the API says our token has expired: clears the session and returns to login. */
    protected fun forceLogout() {
        try {
            SessionManager(this).clear()
        } catch (e: Exception) {
            // Secure storage unavailable - nothing more to clear.
        }
        Toast.makeText(this, R.string.session_expired, Toast.LENGTH_LONG).show()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
