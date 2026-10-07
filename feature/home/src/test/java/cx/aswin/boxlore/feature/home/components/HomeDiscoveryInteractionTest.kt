package cx.aswin.boxlore.feature.home.components

import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.components.CuratedEpisodeCard
import cx.aswin.boxlore.core.designsystem.components.DiscoveryExpressiveTheme
import cx.aswin.boxlore.core.designsystem.components.FeedMediaCardPresentation
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.home.R
import cx.aswin.boxlore.feature.home.StableEpisodeList
import cx.aswin.boxlore.feature.home.StablePodcastList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeDiscoveryInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `episode card exposes only episode title and opens it once`() {
        var clicks = 0
        val parent = podcast("parent", "Hidden show name")
        val episode = Episode("episode", "Visible episode title", "", "")
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CuratedEpisodeCard(parent, episode, { clicks++ }, showSubtitle = false, presentation = FeedMediaCardPresentation.ExpressivePoster)
            }
        }
        composeRule.onNodeWithText(parent.title).assertDoesNotExist()
        composeRule.onNodeWithText(episode.title).performClick()
        assertEquals(1, clicks)
    }

    @Test fun `featured title and artwork share one episode callback without promotional overlays`() {
        var clicks = 0
        val episode = Episode("featured", "Featured episode", "", "", publishedDate = System.currentTimeMillis() / 1000L)
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CuratedEpisodeCard(
                    podcast("parent", "Hidden show"),
                    episode,
                    { clicks++ },
                    modifier = Modifier.width(328.dp).testTag("featured"),
                    showSubtitle = false,
                    presentation = FeedMediaCardPresentation.ExpressiveFeatured,
                )
            }
        }
        composeRule.onNodeWithText(episode.title).performClick()
        assertEquals(1, clicks)
        composeRule.onNodeWithTag("featured").performTouchInput { click(Offset(width - 24f, height / 2f)) }
        assertEquals(2, clicks)
        composeRule.onNodeWithText("Hidden show").assertDoesNotExist()
        composeRule.onNodeWithText(label(cx.aswin.boxlore.core.designsystem.R.string.feed_media_new)).assertDoesNotExist()
    }

    @Test fun `seed card and change show button invoke independent callbacks once`() {
        val seed = podcast("seed", "Seed show")
        var showClicks = 0
        var swapClicks = 0
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                BecauseYouLikeSection(
                    podcast = seed,
                    recommendations = StableEpisodeList(emptyList()),
                    suggestedPodcasts = StablePodcastList(emptyList()),
                    currentPlayingEpisodeId = null,
                    isPlaying = false,
                    onEpisodeClick = { _, _ -> },
                    onPlayEpisode = { _, _ -> },
                    onPodcastClick = { showClicks++ },
                    onChangePodcastClick = { swapClicks++ },
                )
            }
        }
        composeRule.onNodeWithContentDescription(label(R.string.home_change_recommendation_show)).performClick()
        assertEquals(1, swapClicks)
        assertEquals(0, showClicks)
        composeRule.onNodeWithText(seed.title).performClick()
        assertEquals(1, showClicks)
        assertEquals(1, swapClicks)
    }

    @Test fun `genre selection exposes selected semantics and invokes existing selection callback once`() {
        val selections = mutableListOf<String?>()
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                DiscoveryExpressiveTheme {
                    GenreSelector(selectedCategory = null, onCategorySelected = { selections.add(it) }, modifier = Modifier.width(380.dp))
                }
            }
        }
        composeRule.onNodeWithText(label(R.string.home_genres_top)).assertIsSelected()
        composeRule.onNodeWithText("News").performClick()
        assertEquals(listOf("News"), selections)
        composeRule.onNodeWithText(label(R.string.home_genres_top)).performClick()
        assertEquals(listOf("News", null), selections)
    }

    private fun podcast(id: String, title: String) = Podcast(id = id, title = title, artist = "Hidden artist", imageUrl = "")
    private fun label(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)
}
