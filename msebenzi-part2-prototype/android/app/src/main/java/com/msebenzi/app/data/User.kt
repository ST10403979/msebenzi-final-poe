package com.msebenzi.app.data

data class User(
    val id: Int,
    val name: String,
    val email: String,
    val role: String, // "worker" | "household"
    val language: String? = "en",
    val skills: String? = null,
    val bio: String? = null,
    val verificationStatus: String? = "unverified", // "unverified" | "pending" | "verified"
    val ratingAverage: Double = 0.0,
    val ratingCount: Int = 0,
    val avatarMediaId: Int? = null
)

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String,
    val role: String,
    val language: String = "en"
)

data class LoginRequest(
    val email: String,
    val password: String
)

/** Single sign-on: the Google ID token obtained through Credential Manager. */
data class GoogleLoginRequest(
    val idToken: String,
    val role: String? = null,
    val language: String? = null
)

/**
 * Response for register / login / Google login. For a brand-new Google user the API
 * answers `needsRole = true` (no token yet) so the app can ask "worker or household?".
 */
data class AuthResponse(
    val message: String? = null,
    val token: String? = null,
    val user: User? = null,
    val needsRole: Boolean = false
)

data class UpdateProfileRequest(
    val name: String? = null,
    val language: String? = null,
    val skills: String? = null,
    val bio: String? = null,
    val avatarMediaId: Int? = null
)

data class FcmTokenRequest(val token: String)

/** Returned by POST /api/media after an image BLOB has been stored. */
data class MediaUploadResponse(val id: Int, val url: String? = null)
