package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.CrossPromotionConfidence
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeLink
import cx.aswin.boxlore.core.model.EpisodeLinkKind
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.ResolvedCrossPromotion
import cx.aswin.boxlore.core.model.ShowNotes
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfoPhoneGoldenTest(configuration: PhoneGoldenConfiguration) {
    @get:Rule val golden = PhoneGoldenRule(configuration)
    private val podcast = TestFixtures.podcast(id = "1", title = "Curious world", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        .copy(description = "Stories about the world around us, and the ideas that change how we see it.")
    private val episode = TestFixtures.episode(
        id = "episode-1",
        title = "How we find our way",
        podcastId = "1",
        podcastTitle = podcast.title,
        description = "A conversation about curiosity, memory, and the places we call home.",
        imageUrl = PhoneGoldenRule.ARTWORK_URL,
    ).copy(podcastImageUrl = PhoneGoldenRule.ARTWORK_URL)

    @Test fun podcastInfo() {
        val model = mock(PodcastInfoViewModel::class.java)
        `when`(model.uiState).thenReturn(MutableStateFlow<PodcastInfoUiState>(PodcastInfoUiState.Success(podcast, listOf(episode), true)))
        `when`(model.queuedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadedEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.downloadingEpisodeIds).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePendingDownloadRemoval).thenReturn(MutableStateFlow(null))
        `when`(model.hideCompletedInShowDetails).thenReturn(MutableStateFlow(false))
        `when`(model.episodeTitlesOnly).thenReturn(MutableStateFlow(false))
        `when`(model.isPinnedToHome).thenReturn(MutableStateFlow(false))
        `when`(model.isHidden).thenReturn(MutableStateFlow(false))
        `when`(model.pageShow).thenReturn(MutableStateFlow(cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow()))
        `when`(model.globalSkipBeginningMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalSkipEndingMs).thenReturn(MutableStateFlow(0L))
        `when`(model.globalPlaybackSpeed).thenReturn(MutableStateFlow(1f))
        `when`(model.globalTrimSilence).thenReturn(MutableStateFlow(false))
        `when`(model.autoDownloadBackgroundChecksEnabled).thenReturn(MutableStateFlow(false))
        `when`(model.completedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        stubArchive(model)
        `when`(model.likedEpisodesState).thenReturn(MutableStateFlow(emptySet()))
        `when`(model.episodePlaybackState).thenReturn(MutableStateFlow(emptyMap()))
        render("podcast-info") { PodcastInfoScreen("1", model, {}, { _, _, _ -> }, {}, {}) }
    }

    @Test fun episodeInfo() {
        val model = mock(EpisodeInfoViewModel::class.java)
        stubEpisodeInfoViewModel(
            model,
            MutableStateFlow<EpisodeInfoUiState>(
                EpisodeInfoUiState.Success(episode, "1", podcast.title, relatedEpisodesLoading = false, similarEpisodesLoading = false),
            ),
        )
        render("episode-info") { episodePage(model, episode) }
    }

    /** Audit only: a long Bonus video episode with every section the page can show. */
    @Test fun episodeInfoRich() {
        golden.assumeAuditCapture()
        render("episode-info-rich") { episodePage(richModel(), richEpisode) }
    }

    /** Audit only: the rich page scrolled so the top bar has collapsed. */
    @Test fun episodeInfoScrolled() {
        golden.assumeAuditCapture()
        render("episode-info-scrolled", scrollTo = 2) { episodePage(richModel(), richEpisode) }
    }

    /** Audit only: the rich page scrolled down to its sections (About, chapters, links). */
    @Test fun episodeInfoSections() {
        golden.assumeAuditCapture()
        render("episode-info-sections", scrollTo = 3) { episodePage(richModel(), richEpisode) }
    }

    /** Audit only: the rich page scrolled to its recommendation rows. */
    @Test fun episodeInfoLower() {
        golden.assumeAuditCapture()
        render("episode-info-lower", scrollTo = 6) { episodePage(richModel(), richEpisode) }
    }

    @Test fun episodeInfoTip() {
        golden.assumeAuditCapture()
        // The tip is its own window and goes after four seconds: check it is up, then capture every window.
        render("episode-info-tip", allWindows = true, action = {
            golden.compose.onNodeWithText(golden.compose.activity.getString(R.string.episode_info_mark_tip)).assertExists()
        }) { episodePage(richModel(), richEpisode, showTip = true) }
    }

    @Test fun episodeInfoLoading() {
        golden.assumeAuditCapture()
        val model = mock(EpisodeInfoViewModel::class.java)
        stubEpisodeInfoViewModel(model, MutableStateFlow(EpisodeInfoUiState.Loading))
        render("episode-info-loading") { episodePage(model, richEpisode) }
    }

    @Test fun episodeInfoError() {
        golden.assumeAuditCapture()
        val model = mock(EpisodeInfoViewModel::class.java)
        stubEpisodeInfoViewModel(model, MutableStateFlow(EpisodeInfoUiState.Error))
        render("episode-info-error") { episodePage(model, richEpisode) }
    }

    @Test fun episodeInfoShare() {
        golden.assumeAuditCapture()
        render("episode-info-share", allWindows = true, action = {
            golden.compose.onNodeWithContentDescription(golden.compose.activity.getString(R.string.episode_info_share)).performClick()
            // The captures hold the clock: let the sheet's window open.
            repeat(SHEET_OPEN_STEPS) {
                golden.compose.mainClock.advanceTimeBy(100)
                golden.compose.waitForIdle()
            }
            // An empty scene fails here instead of passing as a picture of the closed page.
            golden.compose.onNodeWithText(golden.compose.activity.getString(cx.aswin.boxlore.core.designsystem.R.string.share_sheet_title_episode)).assertExists()
        }) { episodePage(richModel(), richEpisode) }
    }

    @Composable
    private fun episodePage(model: EpisodeInfoViewModel, shown: Episode, showTip: Boolean = false) {
        EpisodeInfoScreen(
            shown.id, shown.title, shown.description, PhoneGoldenRule.ARTWORK_URL, shown.audioUrl, shown.duration, "1", podcast.title, model,
            {}, {}, {}, {},
            showMarkPlayedTip = showTip,
        )
    }

    private val richEpisode = TestFixtures.episode(
        id = "episode-rich",
        title = "How we find our way home again after a long winter away: memory, maps and the places that keep us",
        podcastId = "1",
        podcastTitle = podcast.title,
        duration = 4_380,
        publishedDate = 1_731_000_000L,
        description = "A conversation about curiosity, memory, and the places we call home.",
        imageUrl = PhoneGoldenRule.ARTWORK_URL,
    ).copy(
        podcastImageUrl = PhoneGoldenRule.ARTWORK_URL,
        episodeType = "bonus",
        enclosureType = "video/mp4",
        seasonNumber = 3,
        episodeNumber = 14,
        persons = listOf(Person("Amara Okafor", "host"), Person("Lukas Brandt", "guest"), Person("Mei Chen", "editor")),
    )

    private fun richModel(): EpisodeInfoViewModel {
        val links = listOf(
            EpisodeLink("https://curiousworld.example/episodes/14", "curiousworld.example", EpisodeLinkKind.WEBSITE),
            EpisodeLink("https://www.youtube.com/watch?v=abc", "youtube.com", EpisodeLinkKind.VIDEO, platform = "YouTube"),
            EpisodeLink("https://www.instagram.com/curiousworld", "instagram.com", EpisodeLinkKind.SOCIAL, platform = "Instagram", handle = "curiousworld"),
            EpisodeLink("https://www.thehistoryblog.co.uk/2024/maps", "thehistoryblog.co.uk", EpisodeLinkKind.ARTICLE, title = "The oldest maps of home"),
            EpisodeLink("https://en.wikipedia.org/wiki/Mental_map", "en.wikipedia.org", EpisodeLinkKind.ARTICLE, title = "Mental map"),
            EpisodeLink("https://www.patreon.com/curiousworld", "patreon.com", EpisodeLinkKind.SUPPORT, platform = "Patreon"),
            EpisodeLink("https://discord.gg/curious", "discord.gg", EpisodeLinkKind.COMMUNITY, platform = "Discord"),
            EpisodeLink("https://podcasts.example/curious", "podcasts.example", EpisodeLinkKind.PODCAST),
            EpisodeLink("mailto:hello@curiousworld.example", "curiousworld.example", EpisodeLinkKind.EMAIL),
            EpisodeLink("https://www.nationalgeographic.com/maps", "nationalgeographic.com", EpisodeLinkKind.ARTICLE, title = "How maps shape memory"),
            EpisodeLink("https://x.com/curiousworld", "x.com", EpisodeLinkKind.SOCIAL, platform = "X", handle = "curiousworld"),
        )
        val chapters = listOf(
            Chapter(0.0, "Cold open"),
            Chapter(95.0, "Why we leave"),
            Chapter(612.0, "The maps we carry in our heads, and the ones we lose"),
            Chapter(1_520.0, "Coming back"),
            Chapter(2_900.0, "Listener stories"),
            Chapter(4_020.0, "What's next"),
        )
        val notes = ShowNotes(
            plainText = "Amara talks with cartographer Lukas Brandt about why the places we grew up in stay with us, " +
                "how memory redraws a map every time we return, and what happens when home changes while we are away. " +
                "Plus listener stories from Lagos, Leipzig and Lisbon, and the oldest maps of home we could find.",
            links = links,
            chapters = chapters,
            html = "",
        )
        val other = TestFixtures.podcast(id = "2", title = "History Snacks", imageUrl = PhoneGoldenRule.ARTWORK_URL)
        val similar = listOf("Welcome to History Snacks!", "The map that started a war", "Lost cities of the north", "Coming home: a listener special")
            .mapIndexed { i, title ->
                TestFixtures.episode(id = "similar-$i", title = title, podcastId = "${2 + i % 3}", podcastTitle = listOf("History Snacks", "Atlas Hour", "Field Notes")[i % 3], imageUrl = PhoneGoldenRule.ARTWORK_URL)
            }
        val related = List(5) { i ->
            TestFixtures.episode(
                id = "related-$i",
                title = "Episode ${13 - i}: ${listOf("Snow lines", "The long way round", "Night trains", "Harbours", "Small towns")[i]}",
                podcastId = "1",
                podcastTitle = podcast.title,
                publishedDate = 1_731_000_000L - (i + 1) * 7L * 86_400,
                imageUrl = PhoneGoldenRule.ARTWORK_URL,
            ).copy(podcastImageUrl = PhoneGoldenRule.ARTWORK_URL, episodeNumber = 13 - i)
        }
        val state = EpisodeInfoUiState.Success(
            episode = richEpisode,
            podcastId = "1",
            podcastTitle = podcast.title,
            resumePositionMs = 4_380_000L * 35 / 100,
            durationMs = 4_380_000L,
            relatedEpisodes = related,
            relatedEpisodesLoading = false,
            similarEpisodes = similar,
            similarEpisodesLoading = false,
            crossPromotion = ResolvedCrossPromotion("History Snacks", CrossPromotionConfidence.HIGH, other, emptyList()),
            showNotes = notes,
            chapters = chapters,
        )
        val model = mock(EpisodeInfoViewModel::class.java)
        stubEpisodeInfoViewModel(model, MutableStateFlow(state), archived = MutableStateFlow(setOf(richEpisode.id)))
        return model
    }

    private fun render(
        name: String,
        scrollTo: Int? = null,
        allWindows: Boolean = false,
        action: (() -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        golden.prepare()
        golden.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = golden.configuration.dark, dynamicColor = false) {
                Surface(Modifier.fillMaxSize(), content = content)
            }
        }
        scrollTo?.let { index ->
            golden.compose.mainClock.advanceTimeBy(1_600)
            // The page's own list comes first; the rows inside it scroll sideways.
            golden.compose.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToIndex(index)
        }
        action?.let {
            golden.compose.mainClock.advanceTimeBy(1_600)
            it()
        }
        golden.capture(name, allWindows)
    }

    companion object {
        private const val SHEET_OPEN_STEPS = 8

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun configurations(): List<Array<Any>> = PhoneGoldenConfiguration.matrix()
    }
}
