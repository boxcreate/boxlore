package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.sherlock.LetterboxKeys
import cx.aswin.boxlore.feature.info.branded.sherlock.SherlockColors
import cx.aswin.boxlore.feature.info.branded.sherlock.SherlockFeed
import cx.aswin.boxlore.feature.info.branded.sherlock.SherlockLetterbox
import cx.aswin.boxlore.feature.info.branded.sherlock.SherlockPage
import cx.aswin.boxlore.feature.info.branded.sherlock.StrandArt
import cx.aswin.boxlore.feature.info.branded.sherlock.StrandContentsLine
import cx.aswin.boxlore.feature.info.branded.sherlock.StrandMotion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SherlockPageTest {
    @Test
    fun `Sherlock Holmes Short Stories gets the Strand page, dark in both themes`() {
        assertSame(SherlockPage, brandedShowPageFor(TestFixtures.podcast(id = "7131280")))
        assertSame(SherlockPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.megaphone.fm/NSR5430340583/")))
        assertSame(SherlockPage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(podcastGuid = "0A245E72-7C89-5DA6-9E43-0041937F1A7C")))
        assertTrue(SherlockPage.darkOnly)
        assertSame(SherlockLetterbox, SherlockPage.subscribeMoment)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable`(dark: Boolean) {
        val style = SherlockPage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
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
        // The header's description, and the printed page of the latest-episode card at its gutter, middle and edge.
        assertReadable(SherlockColors.Muted, SherlockColors.Page)
        for (paper in listOf(SherlockColors.PaperGutter, SherlockColors.Paper, SherlockColors.PaperOuter)) {
            assertReadable(SherlockColors.Print, paper)
            assertReadable(SherlockColors.Body, paper)
            assertReadable(SherlockColors.Italic, paper)
        }
        assertReadable(SherlockColors.DropCapLetter, SherlockColors.Ink)
    }

    @Test
    fun `a title gives its story and its part in Roman numerals`() {
        assertEquals("The Sign of Four", SherlockFeed.story("The Sign of Four: Part Three"))
        assertEquals("III", SherlockFeed.part("The Sign of Four: Part Three"))
        assertEquals("The Adventure of the Bruce-Partington Plans", SherlockFeed.story("The Adventure of the Bruce-Partington Plans: Part Three"))
        assertEquals("VII", SherlockFeed.part("A Study in Scarlet: Part Seven"))
        assertEquals("XII", SherlockFeed.part("The Valley of Fear Part 12"))
        assertEquals("The Valley of Fear", SherlockFeed.story("The Valley of Fear Part 12"))
        // A story told in one episode has no part.
        assertEquals("The Adventure of the Empty House", SherlockFeed.story("The Adventure of the Empty House"))
        assertNull(SherlockFeed.part("The Adventure of the Empty House"))
        assertNull(SherlockFeed.part("A Scandal in Bohemia: Part Zero"))
    }

    @Test
    fun `the card and the contents drop a leading The Adventure of`() {
        assertEquals("The Bruce-Partington Plans", SherlockFeed.shortStory("The Adventure of the Bruce-Partington Plans"))
        assertEquals("The Three Garridebs", SherlockFeed.shortStory("The Adventure of the Three Garridebs"))
        assertEquals("Shoscombe Old Place", SherlockFeed.shortStory("The Adventure of Shoscombe Old Place"))
        assertEquals("The Sign of Four", SherlockFeed.shortStory("The Sign of Four"))
        // Up to 17 characters the title sets on one line; longer ones step down onto two.
        assertFalse(SherlockFeed.isLongTitle("The Sign of Four"))
        assertTrue(SherlockFeed.isLongTitle("The Bruce-Partington Plans"))
    }

    @Test
    fun `the contents list each story once, newest first, numbered as a monthly's pages run`() {
        val feed = listOf(
            episode("a", "The Sign of Four: Part Three"),
            episode("b", "The Sign of Four: Part Two"),
            episode("trailer", "Introducing: The Valley of Fear", type = "trailer"),
            episode("bonus", "A Noiser Christmas Special", type = "bonus"),
            episode("c", "A Study in Scarlet: Part Seven"),
            episode("d", "The Adventure of the Three Garridebs: Part One", type = "full"),
            episode("e", "The Adventure of the Dying Detective: Part One"),
            episode("f", "The Adventure of the Dying Detective: Part Two"),
            // A curly and a straight apostrophe are the same story.
            episode("g", "The Adventure of the Lion’s Mane"),
            episode("h", "The Adventure of the Lion's Mane"),
        )
        assertEquals(
            listOf(
                StrandContentsLine("The Sign of Four", 1),
                StrandContentsLine("A Study in Scarlet", 17),
                StrandContentsLine("The Three Garridebs", 33),
                StrandContentsLine("The Dying Detective", 49),
                StrandContentsLine("The Lion’s Mane", 65),
            ),
            SherlockFeed.contents(feed),
        )
        assertEquals(2, SherlockFeed.contents(feed, count = 2).size)
        assertEquals(emptyList<StrandContentsLine>(), SherlockFeed.contents(emptyList()))
    }

    @Test
    fun `the card's text is the description's first paragraph, without HTML`() {
        // The feed double-encodes its spaces; the credits after the first paragraph are left out.
        val raw = "The police arrive at Pondicherry Lodge… and promptly begin arresting the wrong suspects.&amp;nbsp;\n\n\n\n" +
            "Part three of seven.&amp;nbsp;\n\nA&amp;nbsp;Noiser&amp;nbsp;podcast production."
        assertEquals("The police arrive at Pondicherry Lodge… and promptly begin arresting the wrong suspects.", SherlockFeed.firstParagraph(raw))
        assertEquals("Holmes & Watson’s case.", SherlockFeed.firstParagraph("<p>Holmes &amp; Watson&rsquo;s\n case.</p><p>Credits</p>"))
        assertEquals("One line", SherlockFeed.firstParagraph("<br><br>One <b>line</b>"))
        assertEquals("", SherlockFeed.firstParagraph(""))
        assertEquals("&bogus; stays", SherlockFeed.firstParagraph("&bogus; stays"))
    }

    @Test
    fun `the issue lies closed, slides in and opens on its spine, then rests open`() {
        assertEquals(0f, StrandMotion.slide(0f))
        assertEquals(0f, StrandMotion.leafAngle(0f), 0.001f)
        assertTrue(StrandMotion.showsFront(0f))
        assertEquals(1f, StrandMotion.shade(0f))
        assertEquals(0f, StrandMotion.leftShadow(0f))
        val end = StrandArt.END
        assertEquals(1f, StrandMotion.slide(end))
        assertEquals(-180f, StrandMotion.leafAngle(end))
        assertFalse(StrandMotion.showsFront(end))
        assertEquals(0f, StrandMotion.shade(end), 0.001f)
        assertEquals(1f, StrandMotion.leftShadow(end))
        // The leaf turns one way only: it never swings back.
        val angles = (0..end.toInt() step 10).map { StrandMotion.leafAngle(it.toFloat()) }
        assertTrue(angles.zipWithNext().all { (a, b) -> b <= a })
    }

    @Test
    fun `the issue is delivered and the door gone before Subscribed lands`() {
        assertEquals(0f, LetterboxKeys.door(0f))
        assertEquals(1f, LetterboxKeys.door(600f))
        // The issue comes in from above the slot, hangs at it, then drops out of sight below the button.
        assertTrue(LetterboxKeys.issueY(300f) < -60f)
        assertEquals(0f, LetterboxKeys.issueY(800f), 0.01f)
        assertTrue(LetterboxKeys.issueY(1_000f) >= 80f)
        // The flap is lifted while the issue comes through and shut once it has dropped.
        assertTrue(LetterboxKeys.flap(600f) < -75f)
        assertEquals(0f, LetterboxKeys.flap(1_100f))
        assertEquals(0f, LetterboxKeys.door(1_300f))
        assertTrue(SherlockLetterbox.SNAP_AT in 900f..1_100f)
        assertTrue(SherlockLetterbox.revealDelayMillis < SherlockLetterbox.durationMillis)
    }

    @Test
    fun `keyframes ease each step from its own keyframe and hold outside them`() {
        val frames = arrayOf(Keyframe(0.2f, 10f), Keyframe(0.6f, 30f), Keyframe(1f, 0f))
        assertEquals(10f, keyframes(0f, *frames))
        assertEquals(20f, keyframes(0.4f, *frames), 0.001f)
        assertEquals(15f, keyframes(0.8f, *frames), 0.001f)
        assertEquals(0f, keyframes(1.2f, *frames))
    }

    private fun episode(id: String, title: String, type: String? = null): Episode =
        TestFixtures.episode(id = id, title = title).copy(episodeType = type)

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val dark = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (dark + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
