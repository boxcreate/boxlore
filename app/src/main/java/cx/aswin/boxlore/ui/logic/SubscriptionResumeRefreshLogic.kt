package cx.aswin.boxlore.ui.logic

/**
 * AppRoot skips the first [Lifecycle.Event.ON_START] after onboarding so
 * [SubscriptionForegroundSync.ensureStarted] owns cold start. Later resumes
 * call [SubscriptionForegroundSync.requestRefresh].
 */
internal object SubscriptionResumeRefreshLogic {
    fun shouldRequestRefreshOnStart(isFirstStart: Boolean): Boolean = !isFirstStart
}
