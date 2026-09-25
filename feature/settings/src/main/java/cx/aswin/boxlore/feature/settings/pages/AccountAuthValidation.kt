package cx.aswin.boxlore.feature.settings.pages

import cx.aswin.boxlore.core.auth.RecentLoginRequiredException

private val RECENT_LOGIN_KEYWORDS = listOf(
    "recent-login",
    "recent login",
    "recent_login",
    "recentlogin",
    "recent authentication",
    "requires-recent-login",
    "credential_too_old",
)

private val ACCOUNT_ERROR_MAPPINGS = listOf(
    listOf("user-not-found", "no user") to
        "No account found with this email. Try signing up instead.",
    listOf("wrong-password", "invalid-credential") to
        "Incorrect password or credentials. Please try again.",
    listOf("email-already-in-use", "already registered", "already in use") to
        "This email is already registered. Try signing in instead.",
    listOf("weak-password") to
        "Password is too weak. Please use at least 6 characters.",
    listOf("invalid-email") to
        "Please enter a valid email address.",
    listOf("too-many-requests") to
        "Too many attempts. Please wait a few minutes before trying again.",
    listOf("user-disabled") to
        "This account has been disabled. Please contact support.",
    RECENT_LOGIN_KEYWORDS to
        "For security, please sign out and sign in again before deleting your account.",
    listOf("network") to
        "Network error. Check your connection and try again.",
)

internal const val ERROR_INVALID_EMAIL = "Please enter a valid email address"

internal fun cleanAccountError(raw: String?): String {
    if (raw == null) return "An unexpected error occurred"
    for ((keywords, message) in ACCOUNT_ERROR_MAPPINGS) {
        if (keywords.any { raw.contains(it, ignoreCase = true) }) {
            return message
        }
    }
    return raw
}

internal fun Throwable?.isRecentLoginRequired(): Boolean {
    if (this == null) return false
    var current: Throwable? = this
    while (current != null) {
        if (current is RecentLoginRequiredException) return true
        if (current.javaClass.simpleName.contains("RecentLoginRequired", ignoreCase = true)) return true
        val msg = current.message.orEmpty()
        if (RECENT_LOGIN_KEYWORDS.any { msg.contains(it, ignoreCase = true) }) return true
        current = current.cause
    }
    return false
}

private val EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$".toRegex()

internal fun isValidEmail(email: String): Boolean {
    if (email.isBlank()) return false
    return runCatching {
        android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }.getOrElse {
        EMAIL_REGEX.matches(email)
    }
}

internal fun validatePasswordInputs(
    email: String,
    password: String,
    isSignUp: Boolean,
    confirmPassword: String = "",
): String? = when {
    email.isBlank() -> "Please enter your email address"
    !isValidEmail(email) -> ERROR_INVALID_EMAIL
    password.isBlank() -> "Please enter your password"
    isSignUp && password.length < 6 -> "Password must be at least 6 characters"
    isSignUp && confirmPassword.isBlank() -> "Please confirm your password"
    isSignUp && password != confirmPassword -> "Passwords do not match"
    else -> null
}

internal fun isEmailCollisionError(errorMsg: String): Boolean =
    errorMsg.contains("email-already-in-use", ignoreCase = true) ||
        errorMsg.contains("already in use", ignoreCase = true) ||
        errorMsg.contains("already registered", ignoreCase = true)
