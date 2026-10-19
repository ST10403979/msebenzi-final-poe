package com.msebenzi.app.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Switches the app language at runtime (English, Afrikaans, isiXhosa) using AppCompat's
 * per-app language support, which works from API 24 up and is remembered by Android.
 *
 * Reference: Android Developers (2026) Per-app language preferences.
 */
object LocaleHelper {

    val SUPPORTED = listOf("en", "af", "xh")

    /** Applies [languageTag] ("en" | "af" | "xh") to the whole app (recreates visible screens). */
    fun apply(languageTag: String) {
        val tag = if (languageTag in SUPPORTED) languageTag else "en"
        val shown = current().ifEmpty { deviceLanguage() }
        if (shown != tag) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
        }
    }

    /** The language the app is currently showing ("" if following the device language). */
    fun current(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "" else locales.get(0)?.language ?: ""
    }

    /** The phone's own language if we support it, otherwise English - used for new accounts. */
    fun deviceLanguage(): String {
        val lang = Locale.getDefault().language
        return if (lang in SUPPORTED) lang else "en"
    }
}
