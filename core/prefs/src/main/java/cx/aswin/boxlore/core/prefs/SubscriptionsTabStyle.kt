package cx.aswin.boxlore.core.prefs

/**
 * Subscriptions tabs layout choice:
 * - [TOP] (default): Tabs inside the top header app bar.
 * - [FLOATING]: Floating segmented control FAB at the bottom (like Explore).
 */
object SubscriptionsTabStyle {
    const val TOP = "top"
    const val FLOATING = "floating"
    const val PREF_KEY = "subscriptions_tab_style"

    fun sanitize(value: String?): String =
        if (value?.trim()?.lowercase() == FLOATING) FLOATING else TOP
}
