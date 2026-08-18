package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.model.Episode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastEpisodeSelectionLogicTest {
    @Test
    fun `toggle enters extends and leaves selection`() {
        assertEquals(setOf("b"), PodcastEpisodeSelectionLogic.toggle(emptySet(), "b"))
        assertEquals(setOf("a", "b"), PodcastEpisodeSelectionLogic.toggle(setOf("a"), "b"))
        assertEquals(setOf("a"), PodcastEpisodeSelectionLogic.toggle(setOf("a", "b"), "b"))
    }

    @Test
    fun `older and newer follow newest-first fetched order`() {
        val episodes = listOf(episode("new"), episode("middle"), episode("old"))

        assertEquals(
            setOf("middle", "old"),
            PodcastEpisodeSelectionLogic.addRange(
                selectedIds = setOf("middle"),
                episodes = episodes,
                anchorEpisodeId = "middle",
                range = EpisodeSelectionRange.OLDER,
                newestFirst = true,
            ),
        )
        assertEquals(
            setOf("new", "middle"),
            PodcastEpisodeSelectionLogic.addRange(
                selectedIds = setOf("middle"),
                episodes = episodes,
                anchorEpisodeId = "middle",
                range = EpisodeSelectionRange.NEWER,
                newestFirst = true,
            ),
        )
    }

    @Test
    fun `older follows oldest-first fetched order`() {
        assertEquals(
            setOf("old", "middle"),
            PodcastEpisodeSelectionLogic.addRange(
                selectedIds = setOf("middle"),
                episodes = listOf(episode("old"), episode("middle"), episode("new")),
                anchorEpisodeId = "middle",
                range = EpisodeSelectionRange.OLDER,
                newestFirst = false,
            ),
        )
    }

    @Test
    fun `select all replaces prior ids with complete fetched window`() {
        val selected =
            PodcastEpisodeSelectionLogic.addRange(
                selectedIds = setOf("hidden"),
                episodes = listOf(episode("a"), episode("b")),
                anchorEpisodeId = "a",
                range = EpisodeSelectionRange.ALL,
                newestFirst = true,
            )

        assertEquals(setOf("a", "b"), selected)
    }

    @Test
    fun `visible selection includes only feed cards on screen`() {
        val visible =
            PodcastEpisodeSelectionLogic.visibleEpisodes(
                feedItems =
                    listOf(
                        FeedItem.NormalEpisode(episode("a"), 0),
                        FeedItem.TrailerGroup(listOf(episode("trailer-1") to 1, episode("trailer-2") to 2)),
                        FeedItem.NormalEpisode(episode("hidden"), 3),
                    ),
                visibleItemKeys = setOf("a", "trailer_group_trailer-1"),
            )

        assertEquals(listOf("a", "trailer-1", "trailer-2"), visible.map(Episode::id))
    }

    @Test
    fun `bulk actions preserve visible sorted order and remove duplicates`() {
        val selected =
            PodcastEpisodeSelectionLogic.selectedEpisodes(
                episodes = listOf(episode("b"), episode("a"), episode("b")),
                selectedIds = setOf("a", "b"),
            )

        assertEquals(listOf("b", "a"), selected.map(Episode::id))
    }

    @Test
    fun `completion action becomes unplayed only when every selection is completed`() {
        val selected = listOf(episode("a"), episode("b"))

        assertEquals(
            true,
            PodcastEpisodeSelectionLogic.shouldMarkUnplayed(selected, setOf("a", "b", "other")),
        )
        assertEquals(
            false,
            PodcastEpisodeSelectionLogic.shouldMarkUnplayed(selected, setOf("a")),
        )
        assertEquals(
            false,
            PodcastEpisodeSelectionLogic.shouldMarkUnplayed(emptyList(), setOf("a")),
        )
    }

    private fun episode(id: String): Episode =
        Episode(
            id = id,
            title = id,
            description = "",
            audioUrl = "https://example.com/$id.mp3",
        )
}
