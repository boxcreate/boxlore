package cx.aswin.boxlore.core.designsystem.share

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeShareTarget
import cx.aswin.boxlore.core.model.EpisodeShareTargetSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Episode share links from [ShareManager.episodeShareLink]: the installed source's episode and show
 * targets, the fallback to the episode's own fields when that source fails, and a timestamp or clip
 * kept beside `&show=`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EpisodeShareLinkTest {
    private val episode = Episode(id = "-9", title = "Episode", description = "", audioUrl = "https://cdn.example/9.mp3", podcastId = "920666", recipeId = RECIPE)

    @After
    fun tearDown() {
        ShareManager.episodeShareTargets = EpisodeShareTargetSource.FROM_EPISODE
    }

    @Test
    fun `the installed source names the episode with its show, keeping a timestamp or clip`() = runBlocking {
        ShareManager.episodeShareTargets = EpisodeShareTargetSource { EpisodeShareTarget.Episode(RECIPE.toString(), "920666") }

        val plain = ShareManager.episodeShareLink(episode)
        assertEquals("https://boxlore.in/share?type=episode&id=$RECIPE&show=920666", plain.url)
        assertFalse(plain.opensShow)
        assertEquals("https://boxlore.in/share?type=episode&id=$RECIPE&show=920666&t=90", ShareManager.episodeShareUrl(episode, timestampMs = 90_000))
        assertEquals(
            "https://boxlore.in/share?type=episode&id=$RECIPE&show=920666&start=10&end=20",
            ShareManager.episodeShareUrl(episode, startMs = 10_000, endMs = 20_000),
        )
    }

    @Test
    fun `a show target opens the show and says so`() = runBlocking {
        ShareManager.episodeShareTargets = EpisodeShareTargetSource { EpisodeShareTarget.Show("920666") }

        val link = ShareManager.episodeShareLink(episode, timestampMs = 90_000)

        assertEquals("https://boxlore.in/share?type=podcast&id=920666", link.url)
        assertTrue(link.opensShow)
    }

    @Test
    fun `a failing source falls back to the episode's own fields`() = runBlocking {
        ShareManager.episodeShareTargets = EpisodeShareTargetSource { error("aliases unreadable") }

        assertEquals("https://boxlore.in/share?type=episode&id=$RECIPE&show=920666&t=5", ShareManager.episodeShareUrl(episode, timestampMs = 5_000))
        val phoneOnly = ShareManager.episodeShareLink(episode.copy(recipeId = null))
        assertEquals("https://boxlore.in/share?type=podcast&id=920666", phoneOnly.url)
        assertTrue(phoneOnly.opensShow)
    }

    private companion object {
        const val RECIPE = 4503599627370500L
    }
}
