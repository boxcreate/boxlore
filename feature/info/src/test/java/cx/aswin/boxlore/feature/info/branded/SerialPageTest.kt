package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.serial.SerialCardPose
import cx.aswin.boxlore.feature.info.branded.serial.SerialColors
import cx.aswin.boxlore.feature.info.branded.serial.SerialDeal
import cx.aswin.boxlore.feature.info.branded.serial.SerialEpisodeLine
import cx.aswin.boxlore.feature.info.branded.serial.SerialKeys
import cx.aswin.boxlore.feature.info.branded.serial.SerialMark
import cx.aswin.boxlore.feature.info.branded.serial.SerialOneMore
import cx.aswin.boxlore.feature.info.branded.serial.SerialPage
import cx.aswin.boxlore.feature.info.branded.serial.SerialSeasons
import cx.aswin.boxlore.feature.info.branded.serial.serialDeck
import cx.aswin.boxlore.feature.info.branded.serial.serialEpisodeLine
import cx.aswin.boxlore.feature.info.branded.serial.serialSeasonName
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SerialPageTest {
    private val podcast = TestFixtures.podcast(id = "745392", imageUrl = "https://images.example.invalid/serial.jpg")

    @Test
    fun `Serial gets its stack page, dark in both themes`() {
        assertSame(SerialPage, brandedShowPageFor(TestFixtures.podcast(id = "745392")))
        assertSame(SerialPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.simplecast.com/xl36XBC2/")))
        assertSame(SerialPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(podcastGuid = "2D7400E3-BACB-52FD-AABC-0DA55E39F98B")))
        assertTrue(SerialPage.darkOnly)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable`(dark: Boolean) {
        val style = SerialPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
        assertReadable(c.onSurface, c.surface)
        assertReadable(c.onSurfaceVariant, c.surface)
        assertReadable(c.onSurface, c.surfaceContainerLow)
        assertReadable(c.onPrimary, c.primary)
        assertReadable(style.chips.genreContent, style.chips.genreContainer)
        assertReadable(style.chips.content, style.chips.container)
        assertReadable(style.toolbar.subscribeContent, style.toolbar.subscribeContainer)
        assertReadable(style.toolbar.subscribedContent, style.toolbar.subscribedContainer)
        assertReadable(style.episodeTimeColor, c.surfaceContainerLow)
        assertReadable(style.navigationColors.onSurfaceVariant, style.navigationColors.surfaceContainerHigh)
        assertReadable(style.popup.body, style.popup.popup)
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerLow))
        // The header's own text: the description on the page, the season names on the black cards.
        assertReadable(SerialColors.Muted, SerialColors.Page)
        assertReadable(SerialColors.Text, SerialColors.CardBlack)
        assertReadable(SerialColors.RedText, SerialColors.Card)
    }

    @Test
    fun `the deck shows the newest seven seasons, newest on top`() {
        val deck = serialDeck(emptyList(), podcast)
        assertEquals((18 downTo 12).toList(), deck.map { it.number })
        assertTrue(deck.all { it.cover != null }, "Every season so far ships with its cover")
        assertEquals("18 · METRO", deck.first().label("Season 18", Locale.ROOT))
        assertEquals("12 · THE KIDS OF RUTHERFORD COUNTY", deck.last().label("Season 12", Locale.ROOT))
    }

    @Test
    fun `a new season from the feed goes on top with its first episode's picture and shared name`() {
        val feed = listOf(
            serialEpisode("s19-3", "THE NEXT ONE - Part three", season = 19, number = 3),
            serialEpisode("s19-2", "THE NEXT ONE - Part two", season = 19, number = 2),
            serialEpisode("s19-1", "Introducing THE NEXT ONE - Ep. 1", season = 19, number = 1),
            serialEpisode("s18-3", "METRO - Heavy is the Head", season = 18, number = 3),
        )
        val deck = serialDeck(feed, podcast)
        assertEquals((19 downTo 13).toList(), deck.map { it.number })
        val newest = deck.first()
        assertEquals("THE NEXT ONE", newest.name)
        assertNull(newest.cover)
        assertEquals("https://images.example.invalid/s19-1.jpg", newest.picture)
        // A bundled season keeps its own cover, not the feed's episode pictures.
        assertEquals(SerialSeasons.Bundled.last(), deck[1])
    }

    @Test
    fun `a new season with one episode waits for its name, and falls back to the show's cover`() {
        val feed = listOf(serialEpisode("s19-1", "Introducing THE NEXT ONE - Ep. 1", season = 19, number = 1, picture = podcast.imageUrl))
        val newest = serialDeck(feed, podcast).first()
        assertNull(newest.name)
        assertEquals("SEASON 19", newest.label("Season 19", Locale.ROOT))
        assertEquals(podcast.imageUrl, newest.picture)
    }

    @Test
    fun `a season that gives each episode its own picture shows its trailer's, else episode 1's`() {
        // Like METRO: every episode has its own photo, and the season's art sits on episode 1.
        val metroLike = listOf(
            serialEpisode("s19-3", "NEXT - Three", season = 19, number = 3),
            serialEpisode("s19-2", "NEXT - Two", season = 19, number = 2),
            serialEpisode("s19-1", "Special Preview: NEXT - Ep. 1", season = 19, number = 1),
        )
        assertEquals("https://images.example.invalid/s19-1.jpg", serialDeck(metroLike, podcast).first().picture)
        // A trailer comes first, even before episode 1.
        val trailer = serialEpisode("s19-t", "Introducing NEXT", season = 19).copy(episodeType = "trailer", publishedDate = -1)
        assertEquals("https://images.example.invalid/s19-t.jpg", serialDeck(metroLike + trailer, podcast).first().picture)
        // Neither loaded yet: a picture every loaded episode shares is the season's art; different ones are not.
        val later = listOf(serialEpisode("s19-4", "NEXT - Four", season = 19, number = 4), serialEpisode("s19-5", "NEXT - Five", season = 19, number = 5))
        assertEquals(podcast.imageUrl, serialDeck(later, podcast).first().picture)
        val shared = later.map { it.copy(imageUrl = "https://images.example.invalid/next-cover.jpg") }
        assertEquals("https://images.example.invalid/next-cover.jpg", serialDeck(shared, podcast).first().picture)
    }

    @Test
    fun `Serial's header gets the trailers too, other pages' headers do not`() {
        assertTrue(SerialPage.headerKeepsTrailers)
        assertTrue(BrandedShowPages.all.filter { it !== SerialPage }.none { it.headerKeepsTrailers })
        val trailer = serialEpisode("t", "Introducing NEXT", season = 19).copy(episodeType = "trailer", duration = 120, publishedDate = 5)
        val full = serialEpisode("e", "NEXT - One", season = 19, number = 1).copy(publishedDate = 9)
        assertEquals(listOf(full), brandedFeed(listOf(trailer, full)))
        assertEquals(listOf(full, trailer), brandedFeed(listOf(trailer, full), keepTrailers = true))
    }

    @Test
    fun `a season's name is the title prefix two of its episodes share`() {
        assertEquals("METRO", serialSeasonName(listOf(serialEpisode("a", "METRO - One"), serialEpisode("b", "METRO - Two"), serialEpisode("c", "Special Preview: METRO with Sarah Koenig - Ep. 1"))))
        assertNull(serialSeasonName(listOf(serialEpisode("a", "METRO - One"), serialEpisode("b", "No prefix here"))))
    }

    @Test
    fun `the latest episode's card names its season and number above a title without the season in front`() {
        assertEquals(
            SerialEpisodeLine("METRO", 3, "Heavy is the Head that Refuses to Share the Crown"),
            serialEpisodeLine(serialEpisode("a", "METRO - Heavy is the Head that Refuses to Share the Crown", season = 18, number = 3)),
        )
        // Only a number after the name: the title stays whole.
        assertEquals(SerialEpisodeLine("The Idiot", 5, "The Idiot - Chapter 5"), serialEpisodeLine(serialEpisode("b", "The Idiot - Chapter 5", season = 16, number = 5)))
        assertEquals(SerialEpisodeLine("The Last 12 Weeks", 5, "The Last 12 Weeks - Ep. 5"), serialEpisodeLine(serialEpisode("c", "The Last 12 Weeks - Ep. 5", season = 17, number = 5)))
        // The bundled name wins over a prefix that is not the season's name, and that title stays whole.
        val preview = "Special Preview: METRO with Sarah Koenig - Ep. 1"
        assertEquals(SerialEpisodeLine("METRO", 1, preview), serialEpisodeLine(serialEpisode("d", preview, season = 18, number = 1)))
        // A new season takes its name from the title; no season and no prefix leaves no name.
        assertEquals(SerialEpisodeLine("THE NEXT ONE", 2, "Part two"), serialEpisodeLine(serialEpisode("e", "THE NEXT ONE - Part two", season = 19, number = 2)))
        assertEquals(SerialEpisodeLine(null, null, "A bonus"), serialEpisodeLine(serialEpisode("f", "A bonus")))
    }

    @Test
    fun `keyframes ease each stretch on its own`() {
        val keys = SerialKeys({ it * it }, 0f to 0f, 0.5f to 10f, 1f to 0f)
        assertEquals(0f, keys.at(-1f))
        assertEquals(2.5f, keys.at(0.25f), 1e-4f)
        assertEquals(10f, keys.at(0.5f), 1e-4f)
        assertEquals(7.5f, keys.at(0.75f), 1e-4f)
        assertEquals(0f, keys.at(2f))
    }

    @Test
    fun `the deck deals oldest first, each card 150ms after the one under it, and Serial's card comes out last`() {
        val cards = SerialSeasons.SHOWN
        val landings = (0 until cards).map { SerialDeal.cardAt(it, cards) }
        assertEquals(1_050f, landings.first(), "The top card lands last")
        assertEquals(150f, landings.last(), "The deepest card lands first")
        assertTrue(landings.zipWithNext().all { (above, below) -> above - below == 150f })
        assertEquals(SerialCardPose.Hidden, SerialDeal.card(0, cards, 1_049f))
        assertEquals(SerialCardPose.Rest, SerialDeal.card(0, cards, 1_050f + SerialDeal.CARD_MILLIS))
        assertTrue(SerialDeal.card(0, cards, SerialDeal.PEEK_AT).alpha == 1f, "Every season is down before Serial's card moves")
        assertEquals(SerialCardPose.Hidden, SerialDeal.peek(SerialDeal.PEEK_AT))
        assertEquals(SerialCardPose(0f, 0f, SerialDeal.PEEK_TILT, 1f, 1f), SerialDeal.peek(SerialDeal.END))
        val mid = SerialDeal.card(3, cards, SerialDeal.cardAt(3, cards) + SerialDeal.CARD_MILLIS * 0.5f)
        assertTrue(mid.alpha == 1f && mid.y > -46f && mid.y < 2.5f, "Halfway down, a card is in sight and falling: $mid")
    }

    @Test
    fun `the subscribe moment settles on Subscribed as it ends`() {
        assertEquals(1_240, SerialOneMore.durationMillis)
        assertTrue(SerialOneMore.revealDelayMillis < SerialOneMore.durationMillis)
        assertTrue(SerialOneMore.LAND_AT < SerialOneMore.revealDelayMillis)
    }

    @Test
    fun `the traced S stays whole, inside its card`() {
        val nodes = PathParser().parsePathString(SerialMark.OUTLINE).toNodes()
        val points = nodes.mapNotNull {
            when (it) {
                is PathNode.MoveTo -> it.x to it.y
                is PathNode.LineTo -> it.x to it.y
                else -> null
            }
        }
        assertTrue(points.size > 100, "A smooth outline: ${points.size} points")
        assertTrue(nodes.last() is PathNode.Close)
        // As traced: the S spans about 70..346 across and 160..454 down a 434 by 524 card.
        assertTrue(points.all { (x, y) -> x in 60f..360f && y in 150f..465f }, "Every point inside the S's box")
        assertTrue(points.minOf { it.first } < 75f && points.maxOf { it.first } > 340f)
        assertTrue(points.minOf { it.second } < 165f && points.maxOf { it.second } > 450f)
    }

    private fun serialEpisode(id: String, title: String, season: Int? = null, number: Int? = null, picture: String? = null): Episode =
        TestFixtures.episode(id = id, title = title, imageUrl = picture ?: "https://images.example.invalid/$id.jpg")
            .copy(seasonNumber = season, episodeNumber = number, publishedDate = (number ?: 0).toLong())

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
