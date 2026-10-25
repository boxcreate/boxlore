package cx.aswin.boxlore.core.model

object ShareLinkBuilder {
    private const val BASE_URL = "https://boxlore.in/share"

    fun podcast(id: String): String = "$BASE_URL?type=podcast&id=$id"

    /**
     * An episode link. [showId], when given, names the episode's show (`&show=`) so a phone can find
     * an episode it has no row for in that show's feed; builds that don't know the parameter ignore it.
     */
    fun episode(id: String, timestampMs: Long? = null, startMs: Long? = null, endMs: Long? = null, showId: String? = null,): String {
        val show = showId?.takeIf(String::isNotBlank)?.let { "&show=$it" }.orEmpty()
        val baseUrl = "$BASE_URL?type=episode&id=$id$show"
        return when {
            startMs != null && endMs != null -> {
                "$baseUrl&start=${startMs / 1000}&end=${endMs / 1000}"
            }
            timestampMs != null && timestampMs > 0 -> {
                "$baseUrl&t=${timestampMs / 1000}"
            }
            else -> baseUrl
        }
    }

    fun build(type: String, id: String, timestampMs: Long? = null, startMs: Long? = null, endMs: Long? = null): String = if (type == "podcast") {
        podcast(id)
    } else {
        episode(
            id = id,
            timestampMs = timestampMs,
            startMs = startMs,
            endMs = endMs
        )
    }

    /**
     * The link for an episode share [target]: the episode with its show, or the show alone when the
     * episode has no ID another phone could open.
     *
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
     */
    fun forTarget(target: EpisodeShareTarget, timestampMs: Long? = null, startMs: Long? = null, endMs: Long? = null,): String = when (target) {
        is EpisodeShareTarget.Episode -> episode(target.id, timestampMs, startMs, endMs, target.showId)
        is EpisodeShareTarget.Show -> podcast(target.showId)
    }
}
