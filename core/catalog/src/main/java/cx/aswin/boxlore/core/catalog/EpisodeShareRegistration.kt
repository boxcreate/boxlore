package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.model.EpisodeShareIds
import cx.aswin.boxlore.core.model.EpisodeShareTarget
import cx.aswin.boxlore.core.model.EpisodeShareTargetSource
import cx.aswin.boxlore.core.network.BoxLoreApiV2
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
     * enclosure URL when it is the episode's audio address ([audioUrl]), else as its GUID, so the API
     * finds the same item in the feed. Null when the show or key can't be registered.
     */
    fun requestFor(showId: String, catalogKey: String, audioUrl: String?,): V2EpisodeRegisterRequest? {
        val show = EpisodeRecipeId.showIdOrNull(showId)?.takeIf { it < EpisodeRecipeId.MIN } ?: return null
        val key = EpisodeRecipeId.trimKey(catalogKey) ?: return null
        if (key.length > V2EpisodeRegisterRequest.MAX_CATALOG_KEY_LENGTH) return null
        return if (key == EpisodeRecipeId.trimKey(audioUrl)) {
            V2EpisodeRegisterRequest(show = show, enclosureUrl = key)
        } else {
            V2EpisodeRegisterRequest(show = show, guid = key)
        }
    }
}

/**
 * The production [EpisodeShareTargetSource]: which ID an episode share link names, after
 * registering the episode with the API.
 *
 * - The target comes from the episode's trusted alias when it has one (its recipe ID and Podcast
 *   Index show, also for an RSS show that replaced that show), else from the episode's own fields
 *   ([EpisodeShareIds.choose]). A phone-only ID is never shared.
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
        val alias = try {
            aliases.trustedKeyOf(episode.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "alias lookup failed", e)
            null
        }
        val target = alias?.let { key -> EpisodeShareIds.choose(episode.id, key.podcastId, key.recipeId ?: episode.recipeId) }
            ?.takeIf { it is EpisodeShareTarget.Episode && it.showId != null }
            ?: EpisodeShareIds.choose(episode.id, episode.podcastId, episode.recipeId)
        val showId = (target as? EpisodeShareTarget.Episode)?.showId ?: return target
        val catalogKey = alias?.takeIf { it.podcastId == showId }?.catalogKey ?: episode.catalogKey ?: return target
        val request = EpisodeShareRegistrar.requestFor(showId, catalogKey, episode.audioUrl) ?: return target
        val outcome = withTimeoutOrNull(timeoutMs) { register(request) } ?: EpisodeShareRegistrationOutcome.RETRY
        if (outcome == EpisodeShareRegistrationOutcome.RETRY) scheduleRetry(request)
        return target
    }

    companion object {
        /** How long a share waits for the registration before the link goes out anyway. */
        const val DEFAULT_TIMEOUT_MS: Long = 2_000L
        private const val TAG = "EpisodeSharePreparer"
    }
}
