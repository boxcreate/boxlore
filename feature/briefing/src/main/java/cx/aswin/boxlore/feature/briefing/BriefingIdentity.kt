package cx.aswin.boxlore.feature.briefing

import cx.aswin.boxlore.core.designsystem.briefing.briefingPlayerResource
import cx.aswin.boxlore.core.model.Briefing

/**
 * Stable briefing episode id and cover artwork mapping (no Android context).
 */
internal object BriefingIdentity {
    fun episodeId(briefing: Briefing): String = "briefing_${briefing.region}_${briefing.date}"

    fun coverDrawableRes(region: String): Int = briefingPlayerResource(region)
}
