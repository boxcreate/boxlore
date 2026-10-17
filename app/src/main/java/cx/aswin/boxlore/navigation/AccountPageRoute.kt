package cx.aswin.boxlore.navigation

/** Settings opened on Account & sync. Before onboarding finishes, Settings shows the onboarding version. */
internal const val ACCOUNT_PAGE_ROUTE = "settings?page=account"

/** Whether [route] with [page] is already Account & sync, so a sign-in link doesn't stack another copy. */
internal fun isAccountPage(route: String?, page: String?): Boolean =
    route?.startsWith("settings?") == true && page == "account"

/** What to do with a sign-in link's Account page request. */
internal enum class AccountPageRequestAction {
    /** Not newer than the last handled request (including one handled before recreation). */
    Ignore,

    /** New, but Account & sync is already showing: just remember it as handled. */
    MarkHandled,

    /** New: remember it as handled and open Account & sync. */
    Open,
}

internal fun accountPageRequestAction(request: Long, handled: Long, route: String?, page: String?): AccountPageRequestAction = when {
    request <= handled -> AccountPageRequestAction.Ignore
    isAccountPage(route, page) -> AccountPageRequestAction.MarkHandled
    else -> AccountPageRequestAction.Open
}
