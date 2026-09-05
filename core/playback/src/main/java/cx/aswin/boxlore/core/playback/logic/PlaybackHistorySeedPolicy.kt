package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.ListeningHistoryEntity

internal data class PlaybackHistorySeedSource(
    val podcastId: String? = null,
    val episodeTitle: String? = null,
    val episodeImageUrl: String? = null,
    val podcastImageUrl: String? = null,
    val episodeAudioUrl: String? = null,
    val podcastName: String? = null,
    val durationMs: Long = 0L,
    val enclosureType: String? = null,
    val episodeDescription: String? = null,
)

/**
 * Resolves a missing listening-history row from ordered local metadata without performing I/O.
 */
internal object PlaybackHistorySeedPolicy {
    fun build(
        snapshot: PlaybackProgressSnapshot,
        sources: List<PlaybackHistorySeedSource>,
        podcast: PlaybackHistorySeedSource?,
        telemetry: PlaybackHistorySeedSource?,
        nowMs: Long,
    ): ListeningHistoryEntity? {
        val episodeTitle = resolveEpisodeTitle(snapshot, sources, telemetry) ?: return null

        return ListeningHistoryEntity(
            episodeId = snapshot.episodeId,
            podcastId = resolvePodcastId(sources, telemetry),
            episodeTitle = episodeTitle,
            episodeImageUrl = resolveEpisodeImageUrl(snapshot, sources),
            podcastImageUrl = resolvePodcastImageUrl(sources, podcast),
            episodeAudioUrl = resolveEpisodeAudioUrl(snapshot, sources),
            podcastName = resolvePodcastName(snapshot, sources, podcast, telemetry),
            progressMs = snapshot.positionMs.coerceAtLeast(0L),
            durationMs = resolveDurationMs(snapshot, sources),
            isCompleted = false,
            lastPlayedAt = nowMs,
            enclosureType = resolveEnclosureType(snapshot, sources),
            episodeDescription = sources.firstNotNullOfOrNull(PlaybackHistorySeedSource::episodeDescription),
        )
    }

    fun resolvePodcastId(sources: List<PlaybackHistorySeedSource>, telemetry: PlaybackHistorySeedSource?,): String = sources.firstNonBlank(PlaybackHistorySeedSource::podcastId)
        ?: telemetry?.podcastId.nonBlank()
        ?: ""

    private fun resolveEpisodeTitle(
        snapshot: PlaybackProgressSnapshot,
        sources: List<PlaybackHistorySeedSource>,
        telemetry: PlaybackHistorySeedSource?,
    ): String? = sources.firstNonBlank(PlaybackHistorySeedSource::episodeTitle)
        ?: snapshot.episodeTitle.nonBlank()
        ?: telemetry?.episodeTitle.nonBlank()

    private fun resolveDurationMs(snapshot: PlaybackProgressSnapshot, sources: List<PlaybackHistorySeedSource>,): Long = snapshot.durationMs.takeIf { it > 0L }
        ?: sources.firstNotNullOfOrNull { it.durationMs.takeIf { duration -> duration > 0L } }
        ?: 0L

    private fun resolveEpisodeImageUrl(snapshot: PlaybackProgressSnapshot, sources: List<PlaybackHistorySeedSource>,): String? = sources.firstNonBlank(PlaybackHistorySeedSource::episodeImageUrl)
        ?: snapshot.episodeImageUrl.nonBlank()

    private fun resolvePodcastImageUrl(sources: List<PlaybackHistorySeedSource>, podcast: PlaybackHistorySeedSource?,): String? = sources.firstNonBlank(PlaybackHistorySeedSource::podcastImageUrl)
        ?: podcast?.podcastImageUrl.nonBlank()

    private fun resolveEpisodeAudioUrl(snapshot: PlaybackProgressSnapshot, sources: List<PlaybackHistorySeedSource>,): String? = sources.firstNonBlank(PlaybackHistorySeedSource::episodeAudioUrl)
        ?: snapshot.episodeAudioUrl.nonBlank()

    private fun resolvePodcastName(
        snapshot: PlaybackProgressSnapshot,
        sources: List<PlaybackHistorySeedSource>,
        podcast: PlaybackHistorySeedSource?,
        telemetry: PlaybackHistorySeedSource?,
    ): String = sources.firstNonBlank(PlaybackHistorySeedSource::podcastName)
        ?: podcast?.podcastName.nonBlank()
        ?: snapshot.podcastName.nonBlank()
        ?: telemetry?.podcastName.nonBlank()
        ?: ""

    private fun resolveEnclosureType(snapshot: PlaybackProgressSnapshot, sources: List<PlaybackHistorySeedSource>,): String? = sources.firstNonBlank(PlaybackHistorySeedSource::enclosureType)
        ?: snapshot.enclosureType.nonBlank()

    private fun List<PlaybackHistorySeedSource>.firstNonBlank(value: (PlaybackHistorySeedSource) -> String?): String? = firstNotNullOfOrNull { value(it).nonBlank() }

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf(String::isNotEmpty)
}
