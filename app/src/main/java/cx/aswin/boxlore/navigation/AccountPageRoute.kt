package cx.aswin.boxlore.navigation

/** Settings opened on Account & sync. Before onboarding finishes, Settings shows the onboarding version. */
internal const val ACCOUNT_PAGE_ROUTE = "settings?page=account"

/** Whether [route] with [page] is already Account & sync, so a sign-in link doesn't stack another copy. */
internal fun isAccountPage(route: String?, page: String?): Boolean =
    route?.startsWith("settings?") == true && page == "account"
