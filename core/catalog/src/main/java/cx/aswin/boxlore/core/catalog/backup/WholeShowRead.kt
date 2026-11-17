package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ServerListScheme
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException

/**
 * Reads every episode of a show page by page, newest first, to mark the whole show played
 * ([LibraryBackupManager.markAllEpisodesCompleted]). Later pages read the route the first page came
 * from (its [PodcastRepository.EpisodePage.scheme]), so one read never mixes ID schemes. A page that
 * can't be read (it throws, or comes back [PodcastRepository.EpisodePage.failed]) is asked again up
 * to [ATTEMPTS] times; it is never read as the show's end. When it still fails, the episodes read so
 * far come back with [Result.complete] false.
 */
internal object WholeShowRead {
    /** Tries per page, the first included. */
    const val ATTEMPTS = 3

    data class Result(val episodes: List<Episode>, val complete: Boolean)

    suspend fun read(page: suspend (offset: Int, scheme: ServerListScheme?) -> PodcastRepository.EpisodePage): Result {
        val episodes = mutableListOf<Episode>()
        var offset = 0
        var scheme: ServerListScheme? = null
        while (true) {
            val read = readPage(offset, scheme, page) ?: return Result(episodes, complete = false)
            if (offset == 0) scheme = read.scheme
            episodes += read.episodes
            offset += read.sourceCount
            // A page whose rows all lack audio maps to no episodes but is not the end: hasMore decides.
            if (!read.hasMore || read.sourceCount <= 0) return Result(episodes, complete = true)
        }
    }

    /** The page at [offset], asked up to [ATTEMPTS] times; null when it never came back. */
    private suspend fun readPage(
        offset: Int,
        scheme: ServerListScheme?,
        page: suspend (offset: Int, scheme: ServerListScheme?) -> PodcastRepository.EpisodePage,
    ): PodcastRepository.EpisodePage? {
        repeat(ATTEMPTS) {
            try {
                val read = page(offset, scheme)
                if (!read.failed) return read
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Asked again: a strict read already waited for room on the route.
            }
        }
        return null
    }
}
