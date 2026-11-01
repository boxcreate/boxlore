package cx.aswin.boxlore.core.prefs

/**
 * Shared thresholds for NPS scoring and proactive engagement cooldowns.
 */
object EngagementPromptConstants {
    /** Minimum days between unrelated proactive prompts (NPS, milestone review). */
    const val ENGAGEMENT_COOLDOWN_DAYS = 14

    /** Maximum NPS score (inclusive) treated as a detractor for milestone review gating. */
    const val DETRACTOR_SCORE_MAX = 7

    /** Days after installing boxlore, or after a library import, before any review prompt. */
    const val REVIEW_QUIET_DAYS = 2
}
