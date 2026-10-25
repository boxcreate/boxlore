package cx.aswin.boxlore.core.model

/**
 * What an episode share link names ([ShareLinkBuilder.forTarget]).
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
 */
sealed interface EpisodeShareTarget {
    /**
     * The episode [id] another phone can open. [showId] is its Podcast Index show, sent as `&show=`;
     * null for a show added by its feed address, whose link stays the episode's own ID.
     */
    data class Episode(val id: String, val showId: String?) : EpisodeShareTarget

    /** The episode has no ID another phone could open, so the link opens its show instead. */
    data class Show(val showId: String) : EpisodeShareTarget
}

/**
 * Finds the [EpisodeShareTarget] for an episode about to be shared. The app installs one that also
 * reads the episode's stored aliases and registers the episode with the API.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
 */
fun interface EpisodeShareTargetSource {
    suspend fun targetFor(episode: Episode): EpisodeShareTarget

    companion object {
        /** From the episode's own fields only ([EpisodeShareIds.choose]). */
        val FROM_EPISODE: EpisodeShareTargetSource = EpisodeShareTargetSource { episode ->
            EpisodeShareIds.choose(episode.id, episode.podcastId, episode.recipeId)
        }
    }
}

/**
 * Which ID an episode share link carries.
 *
 * - An episode of a Podcast Index show is shared by its recipe ID ([recipeId], or its own ID when
 *   that is in the recipe range), else by its Podcast Index ID, always with `&show=`. A phone-minted
 *   negative or reserved ID is never shared, because no other phone has it: the link then opens the show.
 * - An episode of a show added by its feed address (`rss:`) has no show ID and only a local ID, so
 *   it keeps the link it always had.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
 * COMPAT(own-ids): shared links name the episode by an ID every build can resolve. Permanent: never remove (MIGRATION.md §5).
 */
object EpisodeShareIds {
    fun choose(localId: String, podcastId: String?, recipeId: Long?,): EpisodeShareTarget {
        // A canonical show ID reads back as the same text, so the link carries it exactly as stored.
        val show = podcastId?.let(EpisodeRecipeId::showIdOrNull)?.takeIf { it < EpisodeRecipeId.MIN }?.toString()
            ?: return EpisodeShareTarget.Episode(localId, null)
        val shared = recipeId?.takeIf(EpisodeRecipeId::isRecipeId) ?: EpisodeRecipeId.showIdOrNull(localId)
        return if (shared != null) EpisodeShareTarget.Episode(shared.toString(), show) else EpisodeShareTarget.Show(show)
    }
}
