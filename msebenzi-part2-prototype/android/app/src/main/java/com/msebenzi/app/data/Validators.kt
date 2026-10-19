package com.msebenzi.app.data

/**
 * Pure input-validation rules, kept free of any Android framework classes
 * (no Context, no android.util.Patterns) so they can run as fast local
 * JVM unit tests without an emulator — see src/test/.../ValidatorsTest.kt.
 */
object Validators {

    private val EMAIL_REGEX = Regex(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$"
    )

    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && EMAIL_REGEX.matches(email)

    fun isValidName(name: String): Boolean =
        name.isNotBlank()

    /** Matches RegisterActivity's existing rule: at least 6 characters. */
    fun isValidPassword(password: String): Boolean =
        password.length >= 6

    /** Login only requires a non-empty password (the account already has one). */
    fun isNonEmptyPassword(password: String): Boolean =
        password.isNotEmpty()

    fun areJobFieldsValid(
        title: String,
        category: String,
        location: String,
        description: String
    ): Boolean =
        title.isNotBlank() && category.isNotBlank() &&
            location.isNotBlank() && description.isNotBlank()

    /** Returns the parsed budget, or null if it's missing/invalid/negative. */
    fun parseBudget(budgetText: String): Double? {
        val budget = budgetText.trim().toDoubleOrNull()
        return if (budget != null && budget >= 0) budget else null
    }
}
