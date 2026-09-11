package cx.aswin.boxlore.core.model

/**
 * Domain representation of an authenticated boxlore cloud account.
 */
data class BoxLoreUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean = false,
    val isAnonymous: Boolean = false,
)
