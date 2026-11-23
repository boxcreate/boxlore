package cx.aswin.boxlore.ui

/**
 * The route a Play install referrer opens: the shared show, or the shared episode with its time,
 * clip and, for newer links, its show ([cx.aswin.boxlore.core.catalog.ReferralIntent.showId]) in the
 * route's `podcastId`, so an episode only its show's feed lists is found on a fresh install too.
 * Null for an unknown type.
 */
internal fun installReferralRoute(referral: cx.aswin.boxlore.core.catalog.ReferralIntent): String? = when (referral.type) {
    "podcast" -> "podcast/${referral.id}?entryPoint=install_referrer"
    "episode" -> {
        val tQuery = if (referral.timestamp != null) "&t=${referral.timestamp}" else ""
        val startQuery = if (referral.start != null) "&start=${referral.start}" else ""
        val endQuery = if (referral.end != null) "&end=${referral.end}" else ""
        val showQuery = referral.showId?.let { "&podcastId=$it" }.orEmpty()
        "episode/${referral.id}?entryPoint=install_referrer$tQuery$startQuery$endQuery$showQuery"
    }
    else -> null
}
