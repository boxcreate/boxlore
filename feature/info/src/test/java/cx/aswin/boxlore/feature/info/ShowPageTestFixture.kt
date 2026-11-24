package cx.aswin.boxlore.feature.info

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodrollItem
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * A regular show page on a mocked ViewModel, in a window and pane the test moves: the rich show
 * (long title, people, a trailer, Support, a podroll) with eight episodes. Toggles the ViewModel
 * owns (sort, hide completed, titles only) change the page's flows as the real ViewModel would.
 */
internal class ShowPageTestFixture(private val rule: PhoneGoldenRule) {
    val window = mutableStateOf(BoxloreTestWindows.PHONE)
    val pane = mutableStateOf<BoxloreLayoutBounds?>(null)
    val podcast: Podcast = TestFixtures.podcast(id = "1", title = LONG_TITLE, artist = "iHeartPodcasts", imageUrl = "").copy(
        description = "Join Holly and Tracy as they bring you the people and events of history, from famous names to the nearly " +
            "forgotten. Every week they dig into a story you half remember and find the parts nobody taught you.",
        genre = "Society & Culture",
        updateFrequency = "2",
        fundingUrl = "https://support.example/history",
        fundingMessage = "Support the show and get every episode early, ad free, with a bonus episode each month too",
        podroll = listOf(PodrollItem("History Snacks", "https://a.example"), PodrollItem("Atlas Hour", "https://b.example"), PodrollItem("Field Notes", "https://c.example")),
    )
    val episodes: List<Episode> = List(8) { index ->
        TestFixtures.episode(id = "e$index", title = "Episode $index", podcastId = "1", publishedDate = 1_731_000_000L - index * 86_400L)
            .copy(persons = if (index == 0) listOf(Person("Holly Frey", "host"), Person("Tracy V. Wilson", "host")) else null)
    }
    val uiState = MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, episodes, isSubscribed = true, hasMoreEpisodes = false))
    val hideCompleted = MutableStateFlow(false)
    val titlesOnly = MutableStateFlow(false)
    val archived = MutableStateFlow(emptySet<String>())
    val model: PodcastInfoViewModel = mock(PodcastInfoViewModel::class.java)
    lateinit var restoration: StateRestorationTester

    init {
        `when`(model.uiState).thenReturn(uiState)
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(hideCompleted)
        `when`(model.episodeTitlesOnly).thenReturn(titlesOnly)
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.isHidden).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalPlaybackSpeed).thenReturn(MutableStateFlow(1f))
        `when`(model.globalTrimSilence).thenReturn(MutableStateFlow(false))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(true))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.folderNames).thenReturn(MutableStateFlow(emptyList()))
        stubArchive(model, archived)
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        doAnswer {
            uiState.update { state ->
                (state as? PodcastInfoUiState.Success)?.let {
                    it.copy(currentSort = if (it.currentSort == EpisodeSort.NEWEST) EpisodeSort.OLDEST else EpisodeSort.NEWEST)
                } ?: state
            }
            null
        }.`when`(model).toggleSort()
        doAnswer {
            hideCompleted.update { !it }
            null
        }.`when`(model).toggleHideCompleted()
        doAnswer {
            titlesOnly.update { !it }
            null
        }.`when`(model).toggleEpisodeTitlesOnly()
    }

    /** Renders the page in [window] (and [pane], when set, as the shell would). */
    fun render() {
        rule.prepare()
        rule.compose.mainClock.autoAdvance = true
        restoration = StateRestorationTester(rule.compose)
        restoration.setContent {
            val size = window.value
            val bounds = pane.value
            CompositionLocalProvider(
                LocalBoxloreWindowLayout provides size,
                LocalBoxlorePaneWidth provides (bounds?.width ?: size.widthDp),
                LocalBoxlorePaneBounds provides bounds,
            ) {
                Box(Modifier.width((bounds?.width ?: size.widthDp).dp).height((bounds?.height ?: size.heightDp).dp)) {
                    BoxLoreTheme(dynamicColor = false) {
                        Surface {
                            PodcastInfoScreen("1", model, {}, { _, _, _ -> }, {}, {})
                        }
                    }
                }
            }
        }
        rule.compose.waitForIdle()
    }

    /** Moves the page to [size] (and [paneBounds]) and lets it settle. */
    fun resize(size: BoxloreWindowLayout, paneBounds: BoxloreLayoutBounds? = null) {
        rule.compose.runOnIdle {
            pane.value = paneBounds
            window.value = size
        }
        rule.compose.waitForIdle()
    }

    /** Bounds in dp of the first node [matcher] finds. */
    fun bounds(matcher: SemanticsMatcher, unmerged: Boolean = false): Rect {
        val rect = rule.compose.onAllNodes(matcher, useUnmergedTree = unmerged).onFirst().getBoundsInRoot()
        return Rect(rect.left.value, rect.top.value, rect.right.value, rect.bottom.value)
    }

    companion object {
        const val LONG_TITLE = "Stuff You Missed in History Class"
    }
}

/** Whether the menu row [label] shows as on: an on/off row's state, or a choice's selection. */
internal fun androidx.compose.ui.test.junit4.ComposeTestRule.menuRowChecked(label: String): Boolean {
    val node = onNode(androidx.compose.ui.test.hasText(label) and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNode()
    val toggle = node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) { null }
    return toggle?.let { it == androidx.compose.ui.state.ToggleableState.On }
        ?: (node.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Selected) { null } == true)
}
