package cx.aswin.boxlore.core.analytics

/**
 * Which in-app announcement an event is about: [id] (its send time), [title] (cut to 80 characters)
 * and [presentation] (`compact` or `fullscreen`), so announcements can be told apart in PostHog.
 */
data class AnnouncementRef(val id: String, val title: String, val presentation: String,)
