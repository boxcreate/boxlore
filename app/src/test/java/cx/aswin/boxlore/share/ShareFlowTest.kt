package cx.aswin.boxlore.share

import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.components.ShareBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.share.ShareManager
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeShareTarget
import cx.aswin.boxlore.core.model.EpisodeShareTargetSource
import cx.aswin.boxlore.core.model.Podcast
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

/**
 * The share flow the way a listener runs it, with the app's real file provider: the share sheet as
 * the player, an episode page and a show page open it, through to what the receiving app gets (the
 * message, the link, the card image) or what lands on the clipboard. What a chat app or Instagram
 * then shows can only be seen on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w412dp-h900dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShareFlowTest {
    @get:Rule val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<Application>()
    private val episode = Episode(
        id = "-9",
        title = "The Buried Bodies Case",
        description = "",
        audioUrl = "https://cdn.example/9.mp3",
        podcastId = "920666",
        recipeId = RECIPE,
        duration = 3_600,
    )
    private val podcast = Podcast(id = "920666", title = "Radiolab", artist = "WNYC Studios", imageUrl = "")
    private var position by mutableStateOf(65_000L)

    /**
     * FileProvider keeps each authority's folders in a static cache, but every Robolectric test gets
     * a fresh data folder; without this the second test's card falls outside the cached folder.
     */
    @Before
    fun forgetEarlierTestFolders() {
        FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.let { (it.get(null) as MutableMap<*, *>).clear() }
    }

    @After
    fun tearDown() {
        ShareManager.episodeShareTargets = EpisodeShareTargetSource.FROM_EPISODE
    }

    private fun showEpisodeSheet(fromPlayer: Boolean) {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(412f, 900f)) {
                    ShareBottomSheet(
                        id = episode.id,
                        type = "episode",
                        title = episode.title,
                        subtitle = podcast.title,
                        onDismissRequest = {},
                        durationMs = episode.duration * 1000L,
                        currentPositionMs = position,
                        showTimestampOption = fromPlayer,
                        episode = episode,
                        onShare = { _, _, timestamp, target ->
                            ShareManager.shareEpisode(context, episode, podcast.title, timestampMs = timestamp, target = target)
                        },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `from the player, the card and the message start where the switch was turned on`() {
        showEpisodeSheet(fromPlayer = true)
        compose.onNodeWithText("Start at 1:05").performClick()
        compose.waitForIdle()
        position = 125_000L // playback carries on while the sheet is open
        compose.waitForIdle()
        compose.onNodeWithText("Start at 1:05").assertExists()

        compose.onNodeWithText("Send artwork card").performClick()
        val shared = awaitShare()

        assertEquals(
            "The Buried Bodies Case · Radiolab\nStarts at 1:05\nhttps://boxlore.in/share?type=episode&id=$RECIPE&show=920666&t=65",
            shared.getStringExtra(Intent.EXTRA_TEXT),
        )
        assertCard(shared, width = 1_200, height = 1_200, name = "player-message")
    }

    @Test
    fun `copying the link from the player keeps the start time`() {
        showEpisodeSheet(fromPlayer = true)
        compose.onNodeWithText("Start at 1:05").performClick()
        compose.waitForIdle()
        position = 125_000L
        compose.onNodeWithText("Copy link").performClick()

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copied = await { clipboard.primaryClip?.getItemAt(0)?.text?.toString() }
        assertEquals("https://boxlore.in/share?type=episode&id=$RECIPE&show=920666&t=65", copied)
        assertEquals("Link copied", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `an episode page shares the whole episode, with no start-time switch`() {
        showEpisodeSheet(fromPlayer = false)
        compose.onNodeWithText("Start at 1:05").assertDoesNotExist()

        compose.onNodeWithText("Send artwork card").performClick()
        val shared = awaitShare()

        assertEquals(
            "The Buried Bodies Case · Radiolab\nhttps://boxlore.in/share?type=episode&id=$RECIPE&show=920666",
            shared.getStringExtra(Intent.EXTRA_TEXT),
        )
        assertCard(shared, width = 1_200, height = 1_200, name = "episode-message")
    }

    @Test
    fun `an episode whose link opens its show drops the start time from the card and the message`() {
        ShareManager.episodeShareTargets = EpisodeShareTargetSource { EpisodeShareTarget.Show("920666") }
        showEpisodeSheet(fromPlayer = true)
        compose.onNodeWithText("Start at 1:05").performClick()
        compose.onNodeWithText("Send artwork card").performClick()
        val shared = awaitShare()

        assertEquals(
            "The Buried Bodies Case · Radiolab\nhttps://boxlore.in/share?type=podcast&id=920666",
            shared.getStringExtra(Intent.EXTRA_TEXT),
        )
    }

    @Test
    fun `a show page shares the show's name, who makes it and its link`() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                ShareBottomSheet(
                    id = podcast.id,
                    type = "podcast",
                    title = podcast.title,
                    subtitle = podcast.artist,
                    onDismissRequest = {},
                    onShare = { _, _, _, target -> ShareManager.sharePodcast(context, podcast, target) },
                )
            }
        }
        compose.onNodeWithText("Start at", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Send artwork card").performClick()
        val shared = awaitShare()

        assertEquals("Radiolab · WNYC Studios\nhttps://boxlore.in/share?type=podcast&id=920666", shared.getStringExtra(Intent.EXTRA_TEXT))
        assertCard(shared, width = 1_200, height = 1_200, name = "show-message")
    }

    @Test
    fun `a story opens Instagram with the tall card and copies the link for its sticker`() {
        val instagram = ComponentName("com.instagram.android", "com.instagram.share.StoryActivity")
        shadowOf(context.packageManager).apply {
            addActivityIfNotPresent(instagram)
            addIntentFilterForActivity(
                instagram,
                IntentFilter("com.instagram.share.ADD_TO_STORY").apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataType("image/png")
                }
            )
        }
        showEpisodeSheet(fromPlayer = true)
        compose.onNodeWithText("Start at 1:05").performClick()
        compose.onNodeWithText("Instagram Story").performClick()

        val story = await { shadowOf(context).nextStartedActivity }
        assertEquals("com.instagram.share.ADD_TO_STORY", story.action)
        assertEquals("com.instagram.android", story.`package`)
        assertImage(story.data, width = 1_080, height = 1_920, name = "player-story")
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals("https://boxlore.in/share?type=episode&id=$RECIPE&show=920666&t=65", clipboard.primaryClip?.getItemAt(0)?.text)
        assertEquals("Link copied. Add it to your story with Instagram's link sticker.", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `without Instagram a story falls back to the share sheet with the tall card`() {
        showEpisodeSheet(fromPlayer = false)
        compose.onNodeWithText("Instagram Story").performClick()
        val shared = awaitShare()

        assertEquals("Instagram isn't available. Choose another app.", ShadowToast.getTextOfLatestToast())
        assertCard(shared, width = 1_080, height = 1_920, name = "episode-story-fallback")
    }

    /** The intent the share sheet hands to the app the listener picks. */
    private fun awaitShare(): Intent {
        val chooser = await { shadowOf(context).nextStartedActivity }
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val shared = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(shared)
        assertEquals(Intent.ACTION_SEND, shared!!.action)
        return shared
    }

    private fun assertCard(shared: Intent, width: Int, height: Int, name: String) {
        assertEquals("image/png", shared.type)
        @Suppress("DEPRECATION")
        assertImage(shared.getParcelableExtra(Intent.EXTRA_STREAM), width, height, name)
    }

    /** Reads the card through the app's file provider, as the receiving app does, and keeps a copy to look at. */
    private fun assertImage(uri: Uri?, width: Int, height: Int, name: String) {
        assertNotNull("no card attached", uri)
        assertEquals("${context.packageName}.fileprovider", uri!!.authority)
        val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val card: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull("card isn't a readable image", card)
        assertEquals(width, card!!.width)
        assertEquals(height, card.height)
        File("build/reports/share-flow").apply { mkdirs() }.resolve("$name.png").writeBytes(bytes)
    }

    /** Waits for the share's background work to land, running the main thread in between. */
    private fun <T> await(timeoutMs: Long = 20_000, value: () -> T?): T {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            value()?.let { return it }
            Thread.sleep(25)
        }
        fail("timed out waiting for the share")
        error("unreachable")
    }

    private companion object {
        const val RECIPE = 4503599627370500L
    }
}
