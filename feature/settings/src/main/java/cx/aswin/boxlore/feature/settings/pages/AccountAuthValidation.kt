package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.StringRes
import cx.aswin.boxlore.core.auth.RecentLoginRequiredException
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.settings.R

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
        R.string.account_error_no_account,
    listOf("wrong-password", "invalid-credential") to
        R.string.account_error_wrong_password,
    listOf("email-already-in-use", "already registered", "already in use") to
        R.string.account_error_email_in_use,
    listOf("weak-password") to
        R.string.account_error_weak_password,
    listOf("invalid-email") to
        R.string.account_error_invalid_email_server,
    listOf("too-many-requests") to
        R.string.account_error_too_many,
    listOf("user-disabled") to
        R.string.account_error_disabled,
    RECENT_LOGIN_KEYWORDS to
        R.string.account_error_recent_login,
    listOf("network") to
        R.string.account_error_network,
)

@StringRes
internal val ERROR_INVALID_EMAIL = R.string.account_error_invalid_email

/** Maps a sign-in error to boxlore's words; an error it does not recognise keeps its own text. */
internal fun cleanAccountError(raw: String?): UiText {
    if (raw == null) return UiText.res(R.string.account_error_unexpected)
    for ((keywords, message) in ACCOUNT_ERROR_MAPPINGS) {
        if (keywords.any { raw.contains(it, ignoreCase = true) }) {
            return UiText.res(message)
        }
    }
    return UiText.raw(raw)
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
): UiText? = when {
    email.isBlank() -> UiText.res(R.string.account_error_enter_email)
    !isValidEmail(email) -> UiText.res(ERROR_INVALID_EMAIL)
    password.isBlank() -> UiText.res(R.string.account_error_enter_password)
    isSignUp && password.length < 6 -> UiText.res(R.string.account_error_password_short)
    isSignUp && confirmPassword.isBlank() -> UiText.res(R.string.account_error_confirm_password)
    isSignUp && password != confirmPassword -> UiText.res(R.string.account_error_passwords_differ)
    else -> null
}

internal fun isEmailCollisionError(errorMsg: String): Boolean =
    errorMsg.contains("email-already-in-use", ignoreCase = true) ||
        errorMsg.contains("already in use", ignoreCase = true) ||
        errorMsg.contains("already registered", ignoreCase = true)
