package cx.aswin.boxlore.feature.library

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.resolveFixedThemeColorScheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hub's cards say what's inside, mark empty sections, open with one tap and keep phone size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryHubCardsTest {
    @get:Rule val compose = createComposeRule()

    private val fixtures = LibraryCaptureFixtures
    private val window = mutableStateOf(BoxloreTestWindows.PHONE)

    private fun showHub(
        state: LibraryUiState.Success,
        playlists: LibraryPlaylistsCard? = LibraryPlaylistsCard(onClick = {}, count = 0),
        fontScale: Float = 1f,
    ) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value, LocalDensity provides Density(density.density, fontScale)) {
            BoxLoreTheme(dynamicColor = false) {
                Box(Modifier.size(window.value.widthDp.dp, window.value.heightDp.dp)) {
                    LibraryContent(state, {}, {}, {}, {}, playlists = playlists)
                }
            }
        }
    }

    private val withArtwork = LibraryPlaylistsCard(coverUrls = List(3) { LibraryCaptureFixtures.ART }, onClick = {}, count = 5)

    private fun textLayout(node: SemanticsNode): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    /** Every card's title (and status) ends left of where its collage's art starts. */
    private fun assertTitlesClearOfArtwork(label: String) {
        val titles = compose.onAllNodesWithTag(HUB_CARD_TITLE_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        val collages = compose.onAllNodesWithTag(HUB_CARD_COLLAGE_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(label, titles.size, collages.size)
        for ((title, collage) in titles.zip(collages)) {
            val artStart = collage.boundsInRoot.center.x - CollageArtStartReach.value
            assertTrue("$label: ${title.config[SemanticsProperties.Text]} ends at ${title.boundsInRoot.right}, art starts at $artStart", title.boundsInRoot.right <= artStart + 0.5f)
            assertTrue("$label: ${title.config[SemanticsProperties.Text]} ends with …", !textLayout(title).isLineEllipsized(textLayout(title).lineCount - 1))
        }
    }

    @Test
    fun `cards say what's inside, and empty sections show a medallion instead of the corner icon`() {
        showHub(LibraryUiState.Success(subscribedPodcasts = fixtures.podcasts.take(3)))
        val statuses = compose.onAllNodesWithTag(HUB_CARD_STATUS_TAG, useUnmergedTree = true).fetchSemanticsNodes()
        val texts = statuses.map { it.config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString() }
        assertTrue(texts.toString(), texts.first().startsWith("3 shows"))
        assertTrue(texts.containsAll(listOf("No likes yet", "Nothing downloaded yet", "Nothing played yet", "No playlists yet")))
        // Liked, Downloads, History and Playlists are empty: four medallions.
        assertEquals(4, compose.onAllNodesWithTag(HUB_CARD_MEDALLION_TAG, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `a card opens with one tap and keeps today's phone size`() {
        showHub(fixtures.loaded())
        val card = compose.onNode(hasText("Subscriptions", substring = true) and androidx.compose.ui.test.hasClickAction()).fetchSemanticsNode()
        assertTrue(card.config.contains(SemanticsActions.OnClick))
        assertEquals(120f, card.boundsInRoot.height)
        assertEquals(412f - 32f, card.boundsInRoot.width)
    }

    @Test
    fun `titles stop before the artwork on phones, folds and tablets, and stay on one line on regular phones`() {
        showHub(fixtures.loaded(), withArtwork)
        for (step in with(BoxloreTestWindows) { listOf(PHONE_SMALL, PHONE, COVER_475, FOLD_FLAT, TABLET_PORTRAIT, PHONE_SMALL) }) {
            compose.runOnIdle { window.value = step }
            compose.waitForIdle()
            assertTitlesClearOfArtwork("${step.widthDp}x${step.heightDp}")
            if (step.widthDp <= 430f) {
                // Regular phones keep today's one-line English titles.
                compose.onAllNodesWithTag(HUB_CARD_TITLE_TAG, useUnmergedTree = true).fetchSemanticsNodes().forEach { assertEquals(1, textLayout(it).lineCount) }
            }
        }
    }

    @Test
    @Config(qualifiers = "de-w1000dp-h1000dp-mdpi")
    fun `long German titles at large text wrap before the artwork on a small phone`() {
        window.value = BoxloreTestWindows.PHONE_SMALL
        showHub(fixtures.loaded(), withArtwork, fontScale = 1.3f)
        assertTitlesClearOfArtwork("de fs1.3")
    }

    @Test
    fun `phone collages stay where they were, and tablet collages keep clear of the corner icon`() {
        // Today's phone card: art centred in the end half, 16dp in from the edge.
        assertEquals((412f - 32f - 16f) * 0.75f, hubCardGeometry((412 - 32).dp, HubCardForm.Phone).collageCenter.value, 0.01f)
        // Two-column tablet cards are 336dp at 800x1280 and on a flat Fold 8; wider single-column cards too.
        for (width in 336..700 step 4) {
            val center = hubCardGeometry(width.dp, HubCardForm.Tablet).collageCenter.value
            // The fanned covers reach about 73dp right of the centre; the corner icon starts 48dp from the end.
            assertTrue("$width: $center", center + 73f <= width - 48f)
        }
    }

    @Test
    fun `counts match the pages they open, and History says the day`() {
        val liked = fixtures.liked(3)
        val failed = fixtures.downloads().take(2).map { it.copy(status = DownloadedEpisodeEntity.STATUS_FAILED) }
        val playedNow = fixtures.liked(1).map { it.copy(lastPlayedAt = System.currentTimeMillis()) }
        showHub(LibraryUiState.Success(likedEpisodes = liked + liked.first(), downloadedEpisodes = failed, recentHistory = playedNow))
        val texts = compose.onAllNodesWithTag(HUB_CARD_STATUS_TAG, useUnmergedTree = true).fetchSemanticsNodes().map { it.config[SemanticsProperties.Text].joinToString() }
        // Liked lists each episode once; Downloads counts no failed downloads.
        assertTrue(texts.toString(), "3 episodes" in texts)
        assertTrue(texts.toString(), "Nothing downloaded yet" in texts)
        assertTrue(texts.toString(), "Played today" in texts)
    }

    @Test
    fun `the last-played day follows the calendar, not 24-hour steps`() {
        val utc = TimeZone.getTimeZone("UTC")
        val lateEvening = 1_760_000_000_000L - (1_760_000_000_000L % 86_400_000L) + 23 * 3_600_000L
        assertEquals(localEpochDay(lateEvening, utc) + 1, localEpochDay(lateEvening + 2 * 3_600_000L, utc))
        assertEquals(localEpochDay(lateEvening, utc), localEpochDay(lateEvening - 20 * 3_600_000L, utc))
    }

    @Test
    fun `Playlists has a colour of its own in every theme, with readable text`() {
        val schemes = listOf("violet", "emerald", "sakura", "midnight").flatMap { brand ->
            listOf(
                resolveFixedThemeColorScheme(brand, darkTheme = false, surfaceStyle = SurfaceStyles.CLASSIC_DYNAMIC),
                resolveFixedThemeColorScheme(brand, darkTheme = false, surfaceStyle = SurfaceStyles.STANDARD),
                resolveFixedThemeColorScheme(brand, darkTheme = true, surfaceStyle = SurfaceStyles.STANDARD),
                resolveFixedThemeColorScheme(brand, darkTheme = true, surfaceStyle = SurfaceStyles.AMOLED),
            )
        }
        for (scheme in schemes) {
            val playlists = scheme.playlistsCardContainer()
            for (other in listOf(scheme.secondaryContainer, scheme.tertiaryContainer, scheme.primaryContainer, scheme.surfaceContainerHigh)) {
                assertTrue("$playlists vs $other", playlists != other)
            }
            assertTrue("contrast on $playlists", contrast(playlists, scheme.onPrimaryContainer) >= 4.5f)
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }
}
