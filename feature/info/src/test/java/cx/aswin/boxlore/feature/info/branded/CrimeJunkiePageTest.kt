package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.contentColorFor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkieCaseTitle
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkieColors
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkieFingerprint
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkieFlashlight
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkiePage
import cx.aswin.boxlore.feature.info.branded.crimejunkie.CrimeJunkieTorch
import cx.aswin.boxlore.feature.info.branded.crimejunkie.airedDate
import cx.aswin.boxlore.feature.info.branded.crimejunkie.crimeJunkieCaseTitle
import java.util.Locale
import java.util.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class CrimeJunkiePageTest {
    @Test
    fun `Crime Junkie gets its Fingerprint page`() {
        assertSame(CrimeJunkiePage, brandedShowPageFor(TestFixtures.podcast(id = "743411")))
        assertSame(CrimeJunkiePage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(feedUrl = "https://feeds.simplecast.com/qm_9xx0g/")))
        assertSame(CrimeJunkiePage, brandedShowPageFor(TestFixtures.podcast(id = "x").copy(podcastGuid = "1CD3128E-4576-5CD8-B53D-91F5DA51F32C")))
        assertFalse(CrimeJunkiePage.darkOnly)
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `page text is readable in both themes`(dark: Boolean) {
        val style = CrimeJunkiePage.style(if (dark) darkColorScheme() else lightColorScheme(), dark)
        val c = style.colors
        assertReadable(c.onSurface, c.surface)
        assertReadable(c.onSurfaceVariant, c.surface)
        assertReadable(c.onSurface, c.surfaceContainerLow)
        assertReadable(c.onSurfaceVariant, c.surfaceContainerLow)
        assertReadable(c.onPrimary, c.primary)
        assertReadable(c.primary, c.surfaceContainerLow)
        assertReadable(style.chips.content, style.chips.container)
        assertReadable(style.chips.genreContent, style.chips.genreContainer)
        assertReadable(style.toolbar.subscribeContent, style.toolbar.subscribeContainer)
        assertReadable(style.toolbar.subscribedContent, style.toolbar.subscribedContainer)
        assertReadable(style.toolbar.toggleCheckedContent, style.toolbar.toggleCheckedContainer)
        assertReadable(style.toolbar.actionContent, style.toolbar.actionContainer)
        assertReadable(style.episodeTimeColor, c.surfaceContainerLow)
        assertReadable(style.navigationColors.onSurfaceVariant, style.navigationColors.surfaceContainerHigh)
        // Cards and chips keep full-strength text by default.
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerLow))
        assertEquals(c.onSurface, c.contentColorFor(c.surfaceContainerHigh))
    }

    @Test
    fun `the tag and the copyright popup are readable`() {
        for (p in listOf(CrimeJunkieColors.Light, CrimeJunkieColors.Dark)) {
            assertReadable(p.onTag, p.tag)
            assertReadable(p.copyright.body, p.copyright.popup)
            assertReadable(p.copyright.note, p.copyright.popup)
            assertReadable(p.ink, p.toggle)
        }
    }

    @Test
    fun `a case title splits into its stamp and the name`() {
        assertEquals(CrimeJunkieCaseTitle("MYSTERIOUS DEATH OF", "Heather Turner"), crimeJunkieCaseTitle("MYSTERIOUS DEATH OF: Heather Turner"))
        assertEquals(CrimeJunkieCaseTitle("MISSING", "Kristin Smart"), crimeJunkieCaseTitle("MISSING: Kristin Smart"))
        // A colon inside the name stays with the name.
        assertEquals(CrimeJunkieCaseTitle("MURDERED", "Jane Doe: The Cold Case"), crimeJunkieCaseTitle("MURDERED: Jane Doe: The Cold Case"))
    }

    @Test
    fun `a title without a case label shows whole, with no stamp`() {
        assertEquals(CrimeJunkieCaseTitle(null, "Introducing Crime Junkie"), crimeJunkieCaseTitle("Introducing Crime Junkie"))
        assertEquals(CrimeJunkieCaseTitle(null, "BONUS:"), crimeJunkieCaseTitle("BONUS:"))
        val late = "We sat down with the family of a victim to talk about everything: Part One"
        assertNull(crimeJunkieCaseTitle(late).stamp, "A colon this far in is part of the title")
        assertEquals(late, crimeJunkieCaseTitle(late).name)
    }

    @Test
    fun `the tag types the length in hours and minutes and the day it aired`() {
        assertEquals("45 min", brandedLength(45 * 60))
        assertEquals("1 hr 5 min", brandedLength(65 * 60))
        assertEquals("", brandedLength(30))
        val zone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        try {
            assertEquals("Oct 26", airedDate(1_793_016_000L, Locale.US))
            assertNull(airedDate(0L, Locale.US))
        } finally {
            TimeZone.setDefault(zone)
        }
    }

    @Test
    fun `the print has every ridge with its breaks`() {
        assertEquals(CrimeJunkieFingerprint.RIDGES, CrimeJunkieFingerprint.Breaks.size)
        assertTrue(CrimeJunkieFingerprint.Breaks.all { it.size % 2 == 0 && it.all { length -> length > 0 } })
        // The core ridges close into loops; the outer ones open towards the bottom like arches.
        val core = CrimeJunkieFingerprint.ridge(0)
        assertEquals(core.first().x, core.last().x, 0.01f)
        assertEquals(core.first().y, core.last().y, 0.01f)
        val outer = CrimeJunkieFingerprint.ridge(CrimeJunkieFingerprint.RIDGES - 1)
        assertTrue((outer.first() - outer.last()).getDistance() > 50f)
    }

    @Test
    fun `the flashlight starts in the dark and lands on Subscribed with nothing left over`() {
        assertEquals(CrimeJunkieTorch.DURATION_MILLIS, CrimeJunkieFlashlight.durationMillis)
        assertTrue(CrimeJunkieFlashlight.revealDelayMillis < CrimeJunkieFlashlight.durationMillis)
        assertTrue(CrimeJunkieTorch.dark(0f))
        assertEquals(0f, CrimeJunkieTorch.beam(0f).second)
        assertEquals(0f, CrimeJunkieTorch.beamAlpha(0f))
        val end = CrimeJunkieTorch.DURATION_MILLIS.toFloat()
        assertFalse(CrimeJunkieTorch.dark(end))
        assertTrue(CrimeJunkieTorch.settled(end))
        assertEquals(1f to 1f, CrimeJunkieTorch.subscribedLabel(end))
        assertEquals(1f, CrimeJunkieTorch.subscribeLabelOut(end))
        assertEquals(0f, CrimeJunkieTorch.inkAlpha(end))
        assertEquals(0f, CrimeJunkieTorch.glowAlpha(end))
        assertEquals(1f, CrimeJunkieTorch.lightsOn(end))
    }

    @Test
    fun `the beam sweeps right, swings back and stops on the splatter as it is found`() {
        val found = CrimeJunkieTorch.FOUND_MILLIS.toFloat()
        val (atFind, radius) = CrimeJunkieTorch.beam(found)
        assertEquals(CrimeJunkieTorch.InkCentre.x, atFind.x, 0.001f)
        assertEquals(CrimeJunkieTorch.InkCentre.y, atFind.y, 0.001f)
        assertEquals(30f, radius, 0.001f)
        val farRight = (60..740 step 10).maxOf { CrimeJunkieTorch.beam(it.toFloat()).first.x }
        assertTrue(farRight > 0.85f, "The sweep reaches the right end")
        assertTrue(CrimeJunkieTorch.inkScale(found + 99f) > 1.1f, "The splatter pops when found")
        // The light floods past the button's corners before the dark room goes.
        assertTrue(CrimeJunkieTorch.beam(1_009f).second > 250f)
        assertTrue(CrimeJunkieTorch.lightsOn(1_009f) > 0.9f)
    }

    private fun assertReadable(foreground: Color, background: Color, minimum: Float = 4.5f) {
        val light = maxOf(foreground.luminance(), background.luminance())
        val darkL = minOf(foreground.luminance(), background.luminance())
        val ratio = (light + 0.05f) / (darkL + 0.05f)
        assertTrue(ratio >= minimum, "Contrast $ratio < $minimum for $foreground on $background")
    }
}
