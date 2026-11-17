package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ServerListScheme
import java.io.IOException

/**
 * A show's pages oldest first for one [SeriesEpisodesLoader]: later pages read the route the first
 * page came from ([PodcastRepository.EpisodePage.scheme]), so the loader never mixes ID schemes, and
 * a page that couldn't be read throws instead of ending the show, so the scan fails (Home offers to
 * try again) rather than treating the listener's place as missing.
 */
internal class SeriesOldestPages(private val read: suspend (offset: Int, scheme: ServerListScheme?) -> PodcastRepository.EpisodePage,) {
    private var scheme: ServerListScheme? = null

    suspend fun page(offset: Int): SeriesEpisodesLoader.Page {
        val page = read(offset, if (offset > 0) scheme else null)
        if (page.failed) throw IOException("Episodes page at $offset couldn't be read")
        if (offset == 0) scheme = page.scheme
        return SeriesEpisodesLoader.Page(page.episodes, hasMore = page.hasMore, advance = page.sourceCount)
    }
}
