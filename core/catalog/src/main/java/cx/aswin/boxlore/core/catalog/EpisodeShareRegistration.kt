package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.model.EpisodeShareIds
import cx.aswin.boxlore.core.model.EpisodeShareTarget
import cx.aswin.boxlore.core.model.EpisodeShareTargetSource
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2EpisodeRegisterRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.awaitResponse

/**
 * What one `POST /v2/episodes/register` call achieved.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
 */
enum class EpisodeShareRegistrationOutcome {
    /** The API answered (whether or not it found the episode in the show's feed or list). */
    DONE,

    /** The API can't take this request (400, 404 for an unknown show); trying again won't help. */
    DROPPED,

    /** Offline, a timeout, no API session yet, the daily limit or a server error: try again later. */
    RETRY,
}

/**
 * Registers a shared episode with the API so older builds and web previews can open its link.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
 */
object EpisodeShareRegistrar {
    private val PERMANENT = setOf(400, 404, 413, 422)

    /** One registration attempt. Never throws, except to cancel. */
    suspend fun registerOnce(api: BoxLoreApiV2, request: V2EpisodeRegisterRequest,): EpisodeShareRegistrationOutcome = try {
        val response = api.registerEpisode(request).awaitResponse()
        when {
            response.isSuccessful -> EpisodeShareRegistrationOutcome.DONE
            response.code() in PERMANENT -> EpisodeShareRegistrationOutcome.DROPPED
            else -> EpisodeShareRegistrationOutcome.RETRY
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w("EpisodeShareRegistrar", "episode registration failed", e)
        EpisodeShareRegistrationOutcome.RETRY
    }

    /**
     * The register body for an episode of show [showId] with [catalogKey]: the key is sent as the
     * enclosure URL when it is the episode's audio address ([audioUrl]), else as its GUID together
     * with the audio address, which lets the API match an episode whose GUID differs between the
     * feed and Podcast Index (the key still comes from the GUID). Null when the show or key can't be
     * registered.
     */
    fun requestFor(showId: String, catalogKey: String, audioUrl: String?,): V2EpisodeRegisterRequest? {
        val show = EpisodeRecipeId.showIdOrNull(showId)?.takeIf { it < EpisodeRecipeId.MIN } ?: return null
        val key = EpisodeRecipeId.trimKey(catalogKey) ?: return null
        if (key.length > V2EpisodeRegisterRequest.MAX_CATALOG_KEY_LENGTH) return null
        val audio = EpisodeRecipeId.trimKey(audioUrl)
        return if (key == audio) {
            V2EpisodeRegisterRequest(show = show, enclosureUrl = key)
        } else {
            V2EpisodeRegisterRequest(show = show, guid = key, enclosureUrl = audio?.takeIf { it.length <= V2EpisodeRegisterRequest.MAX_CATALOG_KEY_LENGTH })
        }
    }
}

/**
 * The production [EpisodeShareTargetSource]: which ID an episode share link names, after
 * registering the episode with the API.
 *
 * - The target comes from the episode's trusted alias when it has one (its recipe ID and Podcast
 *   Index show, also for an RSS show that replaced that show), else from the episode's own fields
 *   ([EpisodeShareIds.choose]). A phone-only ID is never shared: an episode of a Podcast Index show
 *   with only such an ID is shared by the recipe ID of its catalog key in the show's namespace, and
 *   only an episode without a usable key falls back to the show's link.
 * - An episode of a Podcast Index show with a known catalog key is registered first, waiting at most
 *   [timeoutMs]. The link goes out whatever the answer; when the API couldn't be reached in time,
 *   [scheduleRetry] registers it in the background.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links).
 * COMPAT(own-ids): shared links name an ID every build can open. Permanent: never remove (MIGRATION.md §5).
 */
class EpisodeSharePreparer(
    private val aliases: EpisodeAliasReads,
    private val register: suspend (V2EpisodeRegisterRequest) -> EpisodeShareRegistrationOutcome,
    private val scheduleRetry: (V2EpisodeRegisterRequest) -> Unit,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : EpisodeShareTargetSource {
    override suspend fun targetFor(episode: Episode): EpisodeShareTarget {
        val alias = trustedKeyOf(episode.id)
        val chosen = alias?.let { key -> EpisodeShareIds.choose(episode.id, key.podcastId, key.recipeId ?: episode.recipeId) }
            ?.takeIf { it is EpisodeShareTarget.Episode && it.showId != null }
            ?: EpisodeShareIds.choose(episode.id, episode.podcastId, episode.recipeId)
        val showId = chosen.showIdOrNull() ?: return chosen
        val catalogKey = alias?.takeIf { it.podcastId == showId }?.catalogKey ?: episode.catalogKey
        if (catalogKey == null) return chosen
        val target = if (chosen is EpisodeShareTarget.Show) recipeTarget(showId, catalogKey) ?: return chosen else chosen
        val request = EpisodeShareRegistrar.requestFor(showId, catalogKey, episode.audioUrl) ?: return target
        val outcome = withTimeoutOrNull(timeoutMs) { register(request) } ?: EpisodeShareRegistrationOutcome.RETRY
        if (outcome == EpisodeShareRegistrationOutcome.RETRY) scheduleRetry(request)
        return target
    }

    private suspend fun trustedKeyOf(episodeId: String): EpisodeAliasKey? = try {
        aliases.trustedKeyOf(episodeId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        android.util.Log.w(TAG, "alias lookup failed", e)
        null
    }

    private fun EpisodeShareTarget.showIdOrNull(): String? = when (this) {
        is EpisodeShareTarget.Episode -> showId
        is EpisodeShareTarget.Show -> showId
    }

    /**
     * The recipe ID of show [showId]'s episode with [catalogKey], from the show's namespace, for an
     * episode the phone holds only under a phone-only ID. Null for a key that isn't a trimmed catalog
     * key, or when the namespace can't be read.
     */
    private suspend fun recipeTarget(showId: String, catalogKey: String): EpisodeShareTarget.Episode? {
        if (EpisodeRecipeId.trimKey(catalogKey) != catalogKey) return null
        val namespace = try {
            aliases.namespaceOf(showId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "namespace lookup failed", e)
            null
        }?.takeIf(V2EpisodeIds::isShowId) ?: return null
        return EpisodeShareTarget.Episode(EpisodeRecipeId.forNamespace(namespace, catalogKey).toString(), showId)
    }

    companion object {
        /** How long a share waits for the registration before the link goes out anyway. */
        const val DEFAULT_TIMEOUT_MS: Long = 2_000L
        private const val TAG = "EpisodeSharePreparer"
    }
}
