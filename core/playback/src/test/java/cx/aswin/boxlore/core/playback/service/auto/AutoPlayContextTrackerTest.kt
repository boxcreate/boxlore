package cx.aswin.boxlore.core.playback.service.auto

import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Android Auto asks to play with the media ID alone. These cover matching that ID back to the list
 * it was tapped in, which is what makes a tapped episode carry on down its list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoPlayContextTrackerTest {
    private val tracker = AutoPlayContextTracker()

    private fun episode(id: String): MediaItem = AutoMediaItemFactory.playable(
        AutoPlayableSpec(mediaId = "episode:$id", title = "Episode $id", podcastTitle = "Show", source = "any", uri = "https://example.com/$id.mp3"),
    )

    private fun ids(match: AutoPlayContextTracker.Match?) = match?.itemsFromHere?.map { it.mediaId }

    @Test
    fun aTappedEpisodePlaysOnThroughTheRestOfItsList() {
        tracker.record("new", AutoBrowseContract.SOURCE_NEW, continues = true, items = listOf(episode("1"), episode("2"), episode("3")))

        val match = tracker.find("2")

        assertEquals(AutoBrowseContract.SOURCE_NEW, match?.source)
        assertEquals(listOf("episode:2", "episode:3"), ids(match))
    }

    @Test
    fun aListThatDoesNotContinuePlaysOnlyTheTappedEpisode() {
        tracker.record("history", AutoBrowseContract.SOURCE_HISTORY, continues = false, items = listOf(episode("1"), episode("2")))

        val match = tracker.find("1")

        assertFalse(match!!.continues)
        assertEquals(listOf("episode:1"), ids(match))
    }

    @Test
    fun anEpisodeInTwoListsBelongsToTheOneSentLast() {
        tracker.record("downloads", AutoBrowseContract.SOURCE_DOWNLOADS, continues = true, items = listOf(episode("1"), episode("9")))
        tracker.record("new", AutoBrowseContract.SOURCE_NEW, continues = true, items = listOf(episode("1"), episode("2")))

        assertEquals(AutoBrowseContract.SOURCE_NEW, tracker.find("1")?.source)

        // Opening Downloads again makes it the list on screen.
        tracker.record("downloads", AutoBrowseContract.SOURCE_DOWNLOADS, continues = true, items = listOf(episode("1"), episode("9")))

        assertEquals(listOf("episode:1", "episode:9"), ids(tracker.find("1")))
    }

    @Test
    fun aListSentAgainReplacesWhatItHeldBefore() {
        tracker.record("new", AutoBrowseContract.SOURCE_NEW, continues = true, items = listOf(episode("1"), episode("2")))
        tracker.record("new", AutoBrowseContract.SOURCE_NEW, continues = true, items = listOf(episode("2")))

        assertNull(tracker.find("1"))
        assertEquals(listOf("episode:2"), ids(tracker.find("2")))
    }

    @Test
    fun foldersAndUnknownEpisodesAreNeverMatched() {
        val folder = AutoMediaItemFactory.browsable(id = "subscription:1", title = "Show")
        tracker.record("home", AutoBrowseContract.SOURCE_CONTINUE, continues = false, items = listOf(folder))

        assertNull(tracker.find("1"))
        assertNull(tracker.find("missing"))
    }

    @Test
    fun onlyTheMostRecentListsAreKept() {
        val small = AutoPlayContextTracker(maxLists = 2)
        small.record("a", "a", continues = true, items = listOf(episode("1")))
        small.record("b", "b", continues = true, items = listOf(episode("2")))
        small.record("c", "c", continues = true, items = listOf(episode("3")))

        assertNull(small.find("1"))
        assertTrue(small.find("2") != null && small.find("3") != null)
    }
}
