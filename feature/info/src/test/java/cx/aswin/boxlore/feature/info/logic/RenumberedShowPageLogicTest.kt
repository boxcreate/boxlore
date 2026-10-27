package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The show page of a show Podcast Index renumbered: the old number's route and the show it now
 * answers as are one page (followed state, the "Missing episodes?" path, artwork colours), and a
 * podcast left from another route never colours the page.
 */
class RenumberedShowPageLogicTest {
    @Test
    fun onlyAnotherNumericShowIdIsARenumbering() {
        assertTrue(RenumberedShowPageLogic.isRenumbered(OLD, NEW))
        assertFalse(RenumberedShowPageLogic.isRenumbered(NEW, NEW))
        assertFalse("a followed feed linked to the show isn't a renumbering", RenumberedShowPageLogic.isRenumbered(OLD, "rss:abc"))
        assertFalse(RenumberedShowPageLogic.isRenumbered("url:https%3A%2F%2Ffeeds.example", NEW))
    }

    @Test
    fun theFollowUnderTheOldNumberCountsSoSubscribeIsNeverOffered() {
        assertTrue(RenumberedShowPageLogic.isFollowed(shownFollowed = false, routeFollowed = true))
        assertTrue(RenumberedShowPageLogic.isFollowed(shownFollowed = true, routeFollowed = false))
        assertFalse(RenumberedShowPageLogic.isFollowed(shownFollowed = false, routeFollowed = false))
    }

    @Test
    fun theRenumberedShowColoursThePageOfItsOldRoute() {
        val state = success(NEW)

        val podcast = RenumberedShowPageLogic.artworkPodcast(state, OLD, PodcastInfoPageShow(routePodcastId = OLD, shownPodcastId = NEW))

        assertEquals(NEW, podcast?.id)
        assertEquals(NEW, RenumberedShowPageLogic.artworkPodcast(success(NEW), NEW, PodcastInfoPageShow())?.id)
    }

    @Test
    fun aPodcastFromAnotherRouteNeverColoursThePageWhileItMoves() {
        // The page moved from the renumbered show's route to another show's: until that show loads,
        // the previous podcast stays in the state and must not colour the new route.
        val previous = success(NEW)

        assertNull(RenumberedShowPageLogic.artworkPodcast(previous, OTHER, PodcastInfoPageShow(routePodcastId = OTHER, shownPodcastId = OTHER)))
        assertNull(RenumberedShowPageLogic.artworkPodcast(previous, OTHER, PodcastInfoPageShow(routePodcastId = OLD, shownPodcastId = NEW)))
        assertNull(RenumberedShowPageLogic.artworkPodcast(PodcastInfoUiState.Loading, OLD, PodcastInfoPageShow(OLD, NEW)))
    }

    @Test
    fun theColoursFollowTheRouteBackAndForth() {
        val pages = listOf(OLD to NEW, OTHER to OTHER, OLD to NEW)
        val shown = pages.map { (route, show) -> RenumberedShowPageLogic.artworkPodcast(success(show), route, PodcastInfoPageShow(route, show))?.id }

        assertEquals(listOf(NEW, OTHER, NEW), shown)
    }

    private fun success(id: String) =
        PodcastInfoUiState.Success(
            podcast = Podcast(id = id, title = "Show $id", artist = "", imageUrl = "https://img.example/$id.jpg"),
            episodes = emptyList(),
            isSubscribed = true,
        )

    private companion object {
        const val OLD = "1258562"
        const val NEW = "7976624"
        const val OTHER = "920666"
    }
}
