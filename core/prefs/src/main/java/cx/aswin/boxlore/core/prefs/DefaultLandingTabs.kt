package cx.aswin.boxlore.core.prefs

/**
 * Default Explore tab when the route does not already pick one.
 * `for_you` is the historical default (bottom-nav Explore).
 */
object ExploreDefaultTab {
    const val FOR_YOU = "for_you"
    const val TOP = "top"
    const val PREF_KEY = "explore_default_tab"

    /** Explore pager: 0 = Top / trending, 1 = For You. */
    const val INDEX_TOP = 0
    const val INDEX_FOR_YOU = 1

    fun sanitize(value: String?): String {
        val normalized = value?.trim()?.lowercase()
        return if (normalized == TOP) TOP else FOR_YOU
    }

    /**
     * Resolves the Explore pager index.
     * A genre [hasCategory] or an explicit nav tab (`trending` / `top` / `for_you`) wins
     * over [preferred].
     */
    fun resolveIndex(navTab: String?, hasCategory: Boolean, preferred: String?,): Int {
        if (hasCategory) return INDEX_TOP
        return when (navTab?.trim()?.lowercase()) {
            "trending", TOP -> INDEX_TOP
            "foryou", FOR_YOU -> INDEX_FOR_YOU
            else -> if (sanitize(preferred) == TOP) INDEX_TOP else INDEX_FOR_YOU
        }
    }
}

/**
 * Default Subscriptions pager tab when the route omits `tab` (nav sentinel [NAV_USE_PREF]).
 * `shows` matches the current Shows | New Episodes switcher default.
 */
object SubscriptionsDefaultTab {
    const val SHOWS = "shows"
    const val NEW_EPISODES = "new_episodes"
    const val PREF_KEY = "subscriptions_default_tab"

    /** Pager: 0 = Shows, 1 = New Episodes. */
    const val INDEX_SHOWS = 0
    const val INDEX_NEW_EPISODES = 1

    /** Nav `tab` default when the caller did not pick Shows or New Episodes. */
    const val NAV_USE_PREF = -1

    fun sanitize(value: String?): String {
        val normalized = value?.trim()?.lowercase()?.replace(' ', '_')
        return if (normalized == NEW_EPISODES || normalized == "latest") {
            NEW_EPISODES
        } else {
            SHOWS
        }
    }

    fun resolveIndex(navTab: Int, preferred: String?,): Int = when (navTab) {
        INDEX_SHOWS, INDEX_NEW_EPISODES -> navTab
        else -> if (sanitize(preferred) == NEW_EPISODES) INDEX_NEW_EPISODES else INDEX_SHOWS
    }
}
