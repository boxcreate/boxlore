package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.model.CuratedCuriosityResponseDto
import cx.aswin.boxlore.core.network.model.DailyCuriosityDto
import kotlinx.coroutines.CancellationException

/**
 * An episode as a `/v2` discovery answer names it: its show ([podcastId], a Podcast Index show ID)
 * and its recipe ID.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 */
data class DiscoveryEpisodeRef(val podcastId: String, val recipeId: Long,)

/**
 * Translates episode IDs between the IDs this phone stores and the recipe IDs the `/v2` discovery
 * routes speak (docs/EPISODE_IDS.md §5).
 *
 * - **Out** ([recipeIds]): a stored ID becomes the recipe ID of its episode before it is sent as a
 *   recommendation seed, an exclusion or a similar-episodes seed. A trusted alias gives the recipe
 *   ID; a stored ID in the recipe range is its own.
 * - **In** ([localOwners]): a recipe ID in an answer becomes the stored ID that owns the episode on
 *   this phone, before the answer is ranked, deduplicated or shown, so an episode the listener
 *   already has under another ID (a Podcast Index ID from an older build, for example) reads its
 *   listener state and is recognised as played.
 *
 * Anything the phone can't place keeps the ID it had. Ambiguous and unresolved aliases are never
 * used. Show IDs are never translated.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 * COMPAT(own-ids): discovery answers and seeds use recipe IDs while listener state may sit under
 * older IDs. Permanent: never remove (MIGRATION.md §5).
 */
interface DiscoveryEpisodeIds {
    /** The recipe ID `/v2` knows each stored ID by, for the IDs the phone can place; others are left out. */
    suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long>

    /** The stored ID that owns each referenced episode on this phone, for the episodes the phone can place. */
    suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, String>

    companion object {
        /**
         * No translation: stored IDs in the recipe range are their own recipe ID and nothing maps to a
         * local owner. The default without the app's wiring (tests, previews).
         */
        val Unmapped: DiscoveryEpisodeIds =
            object : DiscoveryEpisodeIds {
                override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> = ownRecipeIds(episodeIds)

                override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, String> = emptyMap()
            }

        /** Each stored ID that is itself a recipe-range ID, mapped to itself. */
        internal fun ownRecipeIds(episodeIds: Collection<String>): Map<String, Long> = episodeIds
            .mapNotNull { id -> id.toLongOrNull()?.takeIf(EpisodeRecipeId::isRecipeId)?.let { id to it } }
            .toMap()
    }
}

/**
 * [DiscoveryEpisodeIds.localOwners], or none when the alias table can't be read: the answer then
 * keeps its recipe IDs, which still resolve, instead of failing the surface.
 */
internal suspend fun DiscoveryEpisodeIds.ownersOrNone(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, String> {
    if (refs.isEmpty()) return emptyMap()
    return try {
        localOwners(refs)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        android.util.Log.w(DISCOVERY_IDS_TAG, "Local owners unavailable; keeping recipe IDs", error)
        emptyMap()
    }
}

/**
 * [DiscoveryEpisodeIds.recipeIds], or only the stored IDs that are recipe IDs themselves when the
 * alias table can't be read.
 */
internal suspend fun DiscoveryEpisodeIds.recipeIdsOrOwn(episodeIds: Collection<String>): Map<String, Long> = try {
    recipeIds(episodeIds)
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    android.util.Log.w(DISCOVERY_IDS_TAG, "Recipe IDs unavailable; sending stored recipe IDs only", error)
    DiscoveryEpisodeIds.ownRecipeIds(episodeIds)
}

private const val DISCOVERY_IDS_TAG = "DiscoveryEpisodeIds"

/** The ref of an episode a `/v2` answer named, or null when its ID is not a recipe ID or its show is not a Podcast Index show. */
internal fun Episode.discoveryRef(): DiscoveryEpisodeRef? {
    val recipeId = id.toLongOrNull()?.takeIf(EpisodeRecipeId::isRecipeId) ?: return null
    val show = podcastId?.takeIf { EpisodeRecipeId.showIdOrNull(it) != null } ?: return null
    return DiscoveryEpisodeRef(show, recipeId)
}

/**
 * [episodes] from a `/v2` answer with each recipe ID replaced by its local owner, in the same order.
 * When two answers turn out to be one episode on this phone, the first is kept.
 */
internal suspend fun DiscoveryEpisodeIds.toLocalOwners(episodes: List<Episode>): List<Episode> {
    if (episodes.isEmpty()) return episodes
    val owners = ownersOrNone(episodes.mapNotNull { it.discoveryRef() }.distinct())
    if (owners.isEmpty()) return episodes
    return episodes
        .map { episode -> episode.discoveryRef()?.let(owners::get)?.let { episode.copy(id = it) } ?: episode }
        .distinctBy { it.id }
}

/** [podcasts] with each latest episode's recipe ID replaced by its local owner. */
internal suspend fun DiscoveryEpisodeIds.latestToLocalOwners(podcasts: List<Podcast>): List<Podcast> {
    val latest = podcasts.mapNotNull { it.latestEpisode }
    if (latest.isEmpty()) return podcasts
    val owners = ownersOrNone(latest.mapNotNull { it.discoveryRef() }.distinct())
    if (owners.isEmpty()) return podcasts
    return podcasts.map { podcast ->
        val episode = podcast.latestEpisode ?: return@map podcast
        val owner = episode.discoveryRef()?.let(owners::get) ?: return@map podcast
        podcast.copy(latestEpisode = episode.copy(id = owner))
    }
}

/** [chapters] with each related episode's recipe ID replaced by its local owner (briefing chapters). */
internal suspend fun DiscoveryEpisodeIds.relatedToLocalOwners(chapters: List<Chapter>): List<Chapter> {
    val related = chapters.flatMap { it.relatedEpisodes.orEmpty() }
    if (related.isEmpty()) return chapters
    val owners = ownersOrNone(related.mapNotNull { it.discoveryRef() }.distinct())
    if (owners.isEmpty()) return chapters
    return chapters.map { chapter ->
        val episodes = chapter.relatedEpisodes ?: return@map chapter
        chapter.copy(
            relatedEpisodes = episodes.map { episode -> episode.discoveryRef()?.let(owners::get)?.let { episode.copy(id = it) } ?: episode },
        )
    }
}

/** A curiosity deck with each card's recipe ID replaced by its local owner. */
internal suspend fun DiscoveryEpisodeIds.curiosityToLocalOwners(deck: CuratedCuriosityResponseDto): CuratedCuriosityResponseDto {
    val cards = listOfNotNull(deck.questionOfTheDay) + deck.questionsStack
    val refs = cards.mapNotNull { it.discoveryRef() }.distinct()
    if (refs.isEmpty()) return deck
    val owners = ownersOrNone(refs)
    if (owners.isEmpty()) return deck

    fun DailyCuriosityDto.mapped(): DailyCuriosityDto {
        val owner = discoveryRef()?.let(owners::get)?.toLongOrNull() ?: return this
        return copy(episode = episode.copy(id = owner))
    }
    return deck.copy(questionOfTheDay = deck.questionOfTheDay?.mapped(), questionsStack = deck.questionsStack.map { it.mapped() })
}

private fun DailyCuriosityDto.discoveryRef(): DiscoveryEpisodeRef? {
    val recipeId = episode.id.takeIf(EpisodeRecipeId::isRecipeId) ?: return null
    val show = episode.feedId?.takeIf { it in 1..EpisodeRecipeId.SHOW_ID_MAX } ?: return null
    return DiscoveryEpisodeRef(show.toString(), recipeId)
}
