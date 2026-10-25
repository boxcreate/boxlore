package cx.aswin.boxlore.core.catalog

/**
 * The v2 discovery wiring for code that runs outside [PodcastRepository]: the briefing link
 * builders ([BriefingLinks]) and [ChapterRepository]'s briefing related episodes. The app installs
 * it once at startup with the same switch and translator it gives [PodcastRepository]; without
 * that (tests, previews) discovery uses `/v2` and translates nothing.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 */
object DiscoveryRouting {
    @Volatile
    private var v2Discovery: () -> Boolean = { true }

    /** Translates recipe IDs in briefing chapters to the phone's stored IDs. */
    @Volatile
    var episodeIds: DiscoveryEpisodeIds = DiscoveryEpisodeIds.Unmapped
        private set

    /**
     * Installs the app's v2 discovery switch ([RolloutSwitches.v2Discovery]) and translator.
     *
     * LEGACY(own-ids): [v2Discovery] is the fallback to the v1 discovery routes. Remove it at sunset
     * step S7 (MIGRATION.md §6) once the v1 API client methods are removed.
     */
    fun install(v2Discovery: () -> Boolean, episodeIds: DiscoveryEpisodeIds,) {
        this.v2Discovery = v2Discovery
        this.episodeIds = episodeIds
    }

    /** True while discovery uses the `/v2` routes. */
    fun usesV2(): Boolean = v2Discovery()

    /** Restores the defaults (tests only). */
    internal fun resetForTests() {
        install({ true }, DiscoveryEpisodeIds.Unmapped)
    }
}

/**
 * Briefing links the app builds itself, when a briefing answer or a saved briefing episode carries
 * none (a briefing answer's own links are signed and always preferred).
 *
 * Chapters move to `/v2/briefings/chapters/<region>` with v2 discovery, so their related episodes
 * come back under recipe IDs, as the `/v2` briefing answer links them. The transcript stays on
 * `/briefings/transcript/<region>`, as the `/v2` answer links it: it names no episodes. Briefing
 * episode IDs stay `briefing_*` either way.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 */
object BriefingLinks {
    /**
     * The chapters link of [region]'s briefing of [date] (audio [version], when known). [v2] picks
     * the `/v2` path; it follows the app's v2 discovery switch by default.
     */
    fun chaptersUrl(
        region: String,
        date: String,
        version: String?,
        v2: Boolean = DiscoveryRouting.usesV2(),
        baseUrl: String = BuildConfig.BOXLORE_API_BASE_URL,
    ): String {
        val prefix = if (v2) "$baseUrl/v2" else baseUrl
        return "$prefix/briefings/chapters/$region?d=$date${versionParam(version)}"
    }

    /** The transcript link of [region]'s briefing of [date] (audio [version], when known); the same path in both builds. */
    fun transcriptUrl(region: String, date: String, version: String?, baseUrl: String = BuildConfig.BOXLORE_API_BASE_URL,): String =
        "$baseUrl/briefings/transcript/$region?d=$date${versionParam(version)}"

    private fun versionParam(version: String?): String = version?.let { "&v=$it" }.orEmpty()
}
