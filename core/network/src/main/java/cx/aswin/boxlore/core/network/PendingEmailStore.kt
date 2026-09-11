package cx.aswin.boxlore.core.network

/**
 * Storage interface for persisting pending email addresses across app switches during magic link authentication.
 */
interface PendingEmailStore {
    fun getPendingEmail(): String?
    fun setPendingEmail(email: String?)
}
