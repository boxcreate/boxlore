package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.EpisodeLinkResult
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeShareIds
import cx.aswin.boxlore.core.model.EpisodeShareTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which episode page loads may be skipped, and what an unresolved link shows (own-IDs plan section 9). */
class EpisodeLoadRequestsTest {
    private val request = EpisodeLoadRequests.Request("4503599627370500", "")
    private val episode = Episode(id = "-9", title = "t", description = "", audioUrl = "a")

    @Test
    fun `a page showing the requested episode is never reloaded, even under the phone's own ID`() {
        val shown = EpisodeInfoUiState.Success(episode, "920666", "Show", requestedEpisodeId = "4503599627370500")
        assertTrue(EpisodeLoadRequests.isCovered(null, false, shown, "4503599627370500", "920666"))
        assertFalse(EpisodeLoadRequests.isCovered(null, false, shown, "-9", ""))
    }

    @Test
    fun `a running load of the same ID covers a request with no more to go on`() {
        assertTrue(EpisodeLoadRequests.isCovered(request, true, EpisodeInfoUiState.Loading, request.episodeId, ""))
        assertTrue(EpisodeLoadRequests.isCovered(request, false, EpisodeInfoUiState.ReadingFeed, request.episodeId, ""))
        assertTrue(EpisodeLoadRequests.isCovered(request, false, EpisodeInfoUiState.NotFound(""), request.episodeId, ""))
    }

    @Test
    fun `a request that brings the show restarts the load`() {
        assertFalse(EpisodeLoadRequests.isCovered(request, true, EpisodeInfoUiState.Loading, request.episodeId, "920666"))
        assertFalse(EpisodeLoadRequests.isCovered(request, false, EpisodeInfoUiState.NotFound(""), request.episodeId, "920666"))
        val withShow = request.copy(podcastId = "920666")
        assertTrue(EpisodeLoadRequests.isCovered(withShow, true, EpisodeInfoUiState.Loading, request.episodeId, "920666"))
    }

    @Test
    fun `another episode, or a finished load that failed, loads again`() {
        assertFalse(EpisodeLoadRequests.isCovered(request, true, EpisodeInfoUiState.Loading, "other", ""))
        assertFalse(EpisodeLoadRequests.isCovered(request, false, EpisodeInfoUiState.Error, request.episodeId, ""))
        assertFalse(EpisodeLoadRequests.isCovered(request, false, EpisodeInfoUiState.Loading, request.episodeId, ""))
    }

    @Test
    fun `an unresolved link with nothing to show is not found, or the load error when a source was unreachable`() {
        assertEquals(EpisodeInfoUiState.NotFound("920666"), EpisodeLoadRequests.unresolvedState(EpisodeLinkResult.NotFound, "920666", "", ""))
        assertEquals(EpisodeInfoUiState.NotFound(""), EpisodeLoadRequests.unresolvedState(EpisodeLinkResult.NotFound, "unknown", "", ""))
        assertEquals(EpisodeInfoUiState.Error, EpisodeLoadRequests.unresolvedState(EpisodeLinkResult.Unavailable, "920666", "", ""))
    }

    @Test
    fun `a found episode, or details the caller passed in, carry on`() {
        assertNull(EpisodeLoadRequests.unresolvedState(EpisodeLinkResult.Found(episode), "", "", ""))
        assertNull(EpisodeLoadRequests.unresolvedState(EpisodeLinkResult.NotFound, "", "Title", ""))
        assertNull(EpisodeLoadRequests.unresolvedState(EpisodeLinkResult.Unavailable, "", "", "https://cdn.example/a.mp3"))
    }

    @Test
    fun `the page's episode is the loaded one for this route, under the phone's own ID`() {
        val shown = EpisodeInfoUiState.Success(episode, "920666", "Show", requestedEpisodeId = "4503599627370500")
        assertEquals("-9", EpisodeLoadRequests.pageEpisode(shown, "4503599627370500")?.id)
        assertNull(EpisodeLoadRequests.pageEpisode(shown, "other"))
        assertNull(EpisodeLoadRequests.pageEpisode(EpisodeInfoUiState.ReadingFeed, "4503599627370500"))
    }

    @Test
    fun `a loaded episode without a show is shared with the page's show`() {
        val shown = EpisodeInfoUiState.Success(episode, "920666", "Show")
        val shared = EpisodeLoadRequests.shareEpisode(shown)
        assertEquals("920666", shared.podcastId)
        // A phone-only ID is then shared as the show's link, never without its show.
        assertEquals(EpisodeShareTarget.Show("920666"), EpisodeShareIds.choose(shared.id, shared.podcastId, shared.recipeId))
        val recipe = EpisodeLoadRequests.shareEpisode(shown.copy(episode = episode.copy(recipeId = 4503599627370500L)))
        assertEquals(EpisodeShareTarget.Episode("4503599627370500", "920666"), EpisodeShareIds.choose(recipe.id, recipe.podcastId, recipe.recipeId))

        assertNull(EpisodeLoadRequests.shareEpisode(EpisodeInfoUiState.Success(episode, "unknown", "Show")).podcastId)
        assertNull(EpisodeLoadRequests.shareEpisode(EpisodeInfoUiState.Success(episode, "", "Show")).podcastId)
        val own = episode.copy(podcastId = "rss:abc")
        assertEquals("rss:abc", EpisodeLoadRequests.shareEpisode(EpisodeInfoUiState.Success(own, "920666", "Show")).podcastId)
    }
}
