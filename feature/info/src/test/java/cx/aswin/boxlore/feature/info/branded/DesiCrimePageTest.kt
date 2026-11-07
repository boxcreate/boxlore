package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.desicrime.ChithiKeys
import cx.aswin.boxlore.feature.info.branded.desicrime.DesiCrimeArt
import cx.aswin.boxlore.feature.info.branded.desicrime.DesiCrimeCase
import cx.aswin.boxlore.feature.info.branded.desicrime.DesiCrimeChithi
import cx.aswin.boxlore.feature.info.branded.desicrime.DesiCrimeColors
import cx.aswin.boxlore.feature.info.branded.desicrime.DesiCrimeFeed
import cx.aswin.boxlore.feature.info.branded.desicrime.DesiCrimePage
import cx.aswin.boxlore.feature.info.branded.desicrime.DesiCrimeTear
import cx.aswin.boxlore.feature.info.branded.desicrime.Edge
import cx.aswin.boxlore.feature.info.branded.desicrime.NewestMotion
import cx.aswin.boxlore.feature.info.branded.desicrime.boardX
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DesiCrimePageTest {
    private val cover = "https://images.example.invalid/show-cover.png"
    private val podcast = TestFixtures.podcast(id = "7976624", imageUrl = cover)

    @Test
    fun `The Desi Crime Podcast gets its crime board page, dark in both themes`() {
        assertSame(DesiCrimePage, brandedShowPageFor(TestFixtures.podcast(id = "7976624")))
        assertSame(DesiCrimePage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.acast.com/public/shows/6a55242c7987e974eaf4ba29")))
        assertSame(DesiCrimePage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(podcastGuid = "8EE0469F-E82F-54F1-9736-0E24334C51C4")))
        assertTrue(DesiCrimePage.darkOnly)
        assertSame(DesiCrimeChithi, DesiCrimePage.subscribeMoment)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable`(dark: Boolean) {
        val style = DesiCrimePage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
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
        // The hosts' names and the description on the page; the handwriting on the sticky notes and prints.
        assertReadable(DesiCrimeColors.Muted, DesiCrimeColors.Page)
        for (sticky in listOf(Color(0xFFF2EC98), Color(0xFFE9E07F), Color(0xFFDCD06B))) {
            assertReadable(DesiCrimeColors.NoteInk, sticky)
            assertReadable(DesiCrimeColors.RedPen, sticky)
            assertReadable(DesiCrimeColors.NoteDetails, sticky)
        }
        assertReadable(DesiCrimeColors.PrintInk, Color(0xFFE3DCCC))
        assertReadable(DesiCrimeColors.CaseNumber, Color(0xFFF3EFE5), minimum = 3f)
    }

    @Test
    fun `a title gives the case number, the case and its people`() {
        assertEquals(
            DesiCrimeCase(210, "The Burning Car Murder", "Mundill Mahil & Gagandip Singh"),
            DesiCrimeFeed.case("210. The Burning Car Murder | Mundill Mahil & Gagandip Singh"),
        )
        assertEquals(DesiCrimeCase(212, "The Most Hated Indian Killer In America", ""), DesiCrimeFeed.case("212. The Most Hated Indian Killer In America"))
        // The listeners' stories carry no number.
        assertEquals(DesiCrimeCase(null, "Chai & Chithi 36", "Ghost On A Roller Coaster"), DesiCrimeFeed.case("Chai & Chithi 36 | Ghost On A Roller Coaster"))
        assertEquals(DesiCrimeCase(null, "THUG LIFE", ""), DesiCrimeFeed.case("THUG LIFE"))
    }

    @Test
    fun `a print's caption is the case's people, or the case when the title names a guest or nobody`() {
        assertEquals("Mundill Mahil & Gagandip Singh", DesiCrimeFeed.caption(DesiCrimeFeed.case("210. The Burning Car Murder | Mundill Mahil & Gagandip Singh")))
        assertEquals("The Trial of Ajmal Kasab", DesiCrimeFeed.caption(DesiCrimeFeed.case("211. The Trial of Ajmal Kasab | Ft. Ujjwal Nikam")))
        assertEquals("The Trial of Ajmal Kasab", DesiCrimeFeed.caption(DesiCrimeFeed.case("211. The Trial of Ajmal Kasab | ft. Ujjwal Nikam")))
        assertEquals("The Most Hated Indian Killer In America", DesiCrimeFeed.caption(DesiCrimeFeed.case("212. The Most Hated Indian Killer In America")))
    }

    @Test
    fun `the board leads with the newest case and pins earlier cases that have their own pictures`() {
        val feed = listOf(
            episode("212", "212. The Most Hated Indian Killer In America", cover),
            episode("cc36", "Chai & Chithi 36 | Ghost On A Roller Coaster", cover),
            episode("211", "211. The Trial of Ajmal Kasab | Ft. Ujjwal Nikam"),
            episode("cc35", "Chai & Chithi 35 | Scary Stories"),
            episode("210", "210. The Burning Car Murder | Mundill Mahil & Gagandip Singh"),
            episode("209", "209. Murder in Room 1701 | Wendy Albano", cover),
            episode("208", "208. Murder at DuPont Plaza Hotel | Krishna Maharaj"),
        )
        val board = DesiCrimeFeed.board(feed, podcast)
        assertEquals(212, board.newestNumber)
        // 212's picture is the show's cover, so its print is the cover's top: the same template.
        assertEquals(cover, board.newestPicture)
        assertEquals(listOf(211, 210, 208), board.prints.map { it.number })
        assertEquals(listOf("The Trial of Ajmal Kasab", "Mundill Mahil & Gagandip Singh", "Krishna Maharaj"), board.prints.map { it.caption })
        assertEquals("https://images.example.invalid/211.jpg", board.prints.first().picture)
        // A newest case with its own picture shows that picture.
        assertEquals("https://images.example.invalid/211.jpg", DesiCrimeFeed.board(feed.drop(2), podcast).newestPicture)
        assertEquals(1, DesiCrimeFeed.board(feed, podcast, prints = 1).prints.size)
    }

    @Test
    fun `before the feed loads the board shows the cover's print and no number`() {
        val board = DesiCrimeFeed.board(emptyList(), podcast)
        assertNull(board.newestNumber)
        assertEquals(cover, board.newestPicture)
        assertTrue(board.prints.isEmpty())
    }

    @Test
    fun `pieces keep their distance from their own edge as the window widens`() {
        assertEquals(2f, boardX(Edge.Left, 2f, 840f))
        assertEquals(256f + 840f - DesiCrimeArt.WIDTH, boardX(Edge.Right, 256f, 840f))
        assertEquals(112f + (840f - DesiCrimeArt.WIDTH) / 2f, boardX(Edge.Centre, 112f, 840f))
        // At the design's width nothing moves.
        for (edge in Edge.entries) assertEquals(130f, boardX(edge, 130f, DesiCrimeArt.WIDTH))
        // A wide window has room for more prints between the edges'.
        assertEquals(DesiCrimeArt.Slots, DesiCrimeArt.slots(DesiCrimeArt.WIDTH))
        assertEquals(DesiCrimeArt.MAX_PRINTS, DesiCrimeArt.slots(840f).size)
        assertEquals(3, DesiCrimeArt.slots(840f).count { it.captioned })
    }

    @Test
    fun `the torn edge runs across the whole picture, inside the print`() {
        val xs = DesiCrimeTear.Edge.filterIndexed { i, _ -> i % 2 == 0 }
        val ys = DesiCrimeTear.Edge.filterIndexed { i, _ -> i % 2 == 1 }
        assertEquals(0f, xs.first())
        assertTrue(xs.last() > 0.99f)
        assertTrue(xs.zipWithNext().all { (a, b) -> b > a })
        assertTrue(ys.all { it in 0.45f..DesiCrimeTear.DEPTH })
    }

    @Test
    fun `the newest print drops in and settles at its slant`() {
        assertEquals(0f, NewestMotion.pose(0f).alpha)
        val rest = NewestMotion.pose(DesiCrimeArt.END)
        assertEquals(NewestMotion.Pose(0f, 0f, -2.5f, 1f, 1f), rest)
    }

    @Test
    fun `the glass is set down, filled and lifted away before Subscribed lands`() {
        assertEquals(1f, ChithiKeys.slide(240f))
        assertTrue(ChithiKeys.glassY(190f) < -50f)
        assertEquals(0f, ChithiKeys.glassY(DesiCrimeChithi.SET_DOWN_AT + 100f), 0.01f)
        assertEquals(1f, ChithiKeys.fill(900f))
        assertTrue(ChithiKeys.glassY(1_190f) < -60f)
        assertEquals(1f, ChithiKeys.ring(1_200f))
        assertEquals(0f, ChithiKeys.shown(1_220f))
        // The stream is out of sight before the pour and after it.
        assertTrue(ChithiKeys.pourOffset(400f) >= 60f)
        assertTrue(ChithiKeys.pourOffset(1_000f) <= -60f)
        assertTrue(DesiCrimeChithi.revealDelayMillis < DesiCrimeChithi.durationMillis)
    }

    private fun episode(id: String, title: String, picture: String = "https://images.example.invalid/$id.jpg"): Episode =
        TestFixtures.episode(id = id, title = title, imageUrl = picture)

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val dark = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (dark + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
