package cx.aswin.boxlore.navigation

/**
 * Allowlist for FCM / push `target_route` extras that are not full http(s) / app-scheme URIs.
 * Rejects anything outside known in-app destinations.
 */
object PushTargetRouteAllowlist {
    private val exactRoutes = setOf(
        "home",
        "explore",
        "library",
        "settings",
        "debug",
        "onboarding",
        "library/liked",
        "library/history",
        "library/downloads",
        "library/subscriptions",
        "library/downloads/settings",
        "library/auto_downloads/settings",
    )

    private val prefixRoutes = listOf(
        "podcast/",
        "episode/",
        "settings?",
        "explore?",
        "briefing",
        "library/",
    )

    private val appOrWebSchemes = listOf("http://", "https://", "boxlore://", "boxcast://")

    fun isAppOrWebUri(route: String): Boolean =
        appOrWebSchemes.any { route.startsWith(it) }

    fun isAllowed(route: String): Boolean {
        val trimmed = route.trim()
        if (trimmed.isEmpty()) return false
        if (isAppOrWebUri(trimmed)) return true
        if (trimmed in exactRoutes) return true
        return prefixRoutes.any { trimmed.startsWith(it) }
    }

    /**
     * Returns a navigable route string, or null if not allowed.
     * App-scheme URIs are returned unchanged for [androidx.navigation.NavController.handleDeepLink].
     */
    fun sanitize(route: String?): String? {
        if (route.isNullOrBlank()) return null
        val trimmed = route.trim()
        return if (isAllowed(trimmed)) trimmed else null
    }
}
