package cx.aswin.boxlore.core.auth

/**
 * Storage interface for persisting pending email addresses across app switches during magic link authentication.
 */
interface PendingEmailStore {
    fun getPendingEmail(): String?
    fun setPendingEmail(email: String?)
}
