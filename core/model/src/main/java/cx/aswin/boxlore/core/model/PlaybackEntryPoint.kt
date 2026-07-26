package cx.aswin.boxlore.core.model

/**
 * Coarse play provenance for queue/mixtape policy.
 * Analytics still prefer the fine-grained `entry_point` string on [android.os.Bundle]
 * source context when present; non-[GENERIC] values synthesize `entry_point` =
 * [name] lowercase (e.g. [BRIEFING] → `briefing`, [LEARN] → `learn`).
 */
enum class PlaybackEntryPoint {
    GENERIC,
    HOME_MIXTAPE,
    LEARN,

    /** The Boxlore Brief audio (home card or briefing detail). Glossary: `briefing`. */
    BRIEFING,
}
