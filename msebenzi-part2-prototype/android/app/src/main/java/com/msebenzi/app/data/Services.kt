package com.msebenzi.app.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.msebenzi.app.R

/**
 * The catalogue of services Msebenzi offers. The [key] is the lowercase English
 * identifier stored in the database and used by the API for *targeted job alerts*
 * (a worker's skills are matched against a job's category key), so it never changes
 * with the app language - only the displayed name does.
 */
object ServiceCatalog {

    data class Service(
        val key: String,
        @StringRes val nameRes: Int,
        @StringRes val descriptionRes: Int,
        @DrawableRes val imageRes: Int
    )

    val all: List<Service> = listOf(
        Service("cleaning", R.string.service_cleaning, R.string.service_cleaning_desc, R.drawable.img_service_cleaning),
        Service("gardening", R.string.service_gardening, R.string.service_gardening_desc, R.drawable.img_service_gardening),
        Service("handyman", R.string.service_handyman, R.string.service_handyman_desc, R.drawable.img_service_handyman),
        Service("painting", R.string.service_painting, R.string.service_painting_desc, R.drawable.img_service_painting),
        Service("plumbing", R.string.service_plumbing, R.string.service_plumbing_desc, R.drawable.img_service_plumbing),
        Service("electrical", R.string.service_electrical, R.string.service_electrical_desc, R.drawable.img_service_electrical),
        Service("moving", R.string.service_moving, R.string.service_moving_desc, R.drawable.img_service_moving),
        Service("laundry", R.string.service_laundry, R.string.service_laundry_desc, R.drawable.img_service_laundry)
    )

    /** Case-insensitive lookup; returns null for categories typed by older app versions. */
    fun find(key: String?): Service? = all.firstOrNull { it.key.equals(key?.trim(), ignoreCase = true) }

    /** Picture for a category, falling back to the handyman picture for unknown ones. */
    @DrawableRes
    fun imageFor(key: String?): Int = (find(key) ?: all[2]).imageRes
}
