package cx.aswin.boxlore.feature.explore

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.theme.buildBoxLoreTypography
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoreCardTextTest {
    // Full public card text sampled from Lore; no API keys or response metadata.
    private val question = "Why did a Detroit-area teen try to build a nuclear reactor in a potting shed?"
    private val explanation = "A Boy Scout's ambitious project to build a nuclear reactor in a potting shed raises questions about ambition, responsibility, and the dangers of unchecked enthusiasm."

    private fun measure(width: Int, scale: Float = 1f, body: String? = explanation, heading: String = question): List<LoreMeasuredCardText> {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val density = Density(1f, scale)
        val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr)
        val typography = buildBoxLoreTypography(context, 0f)
        return measureLoreCardText(measurer, heading, body, typography.headlineMedium, typography.bodyLarge, density, width)
    }

    @Test
    fun `full long card fits a small portrait viewport without ellipsis or scrolling`() {
        val candidates = measure(width = 296)
        val selected = selectLoreCardText(candidates, heightPx = 288)
        assertTrue("Full card needs ${selected.heightPx}px", selected.heightPx <= 288)
        assertTrue(selected.spec.headingSp >= 24)
    }

    @Test
    fun `larger system text retains its scale and fits a normal portrait viewport`() {
        val candidates = measure(width = 353, scale = 1.3f)
        // The larger play control adds 16dp to the footer on a normal portrait window.
        val selected = selectLoreCardText(candidates, heightPx = 414)
        assertTrue("Scaled full card needs ${selected.heightPx}px", selected.heightPx <= 414)
        assertTrue(selected.spec.headingSp >= 24)
        assertTrue(measure(width = 353, scale = 1f).last().heightPx < candidates.last().heightPx)
    }

    @Test
    fun `every sampled live card fits narrow portrait and enlarged text viewports`() {
        val fixtures = JSONArray(javaClass.getResourceAsStream("/lore-cards.json")!!.bufferedReader().use { it.readText() })
        for (index in 0 until fixtures.length()) {
            val card = fixtures.getJSONObject(index)
            val heading = card.getString("question")
            val body = card.optString("explanation").takeUnless { it == "null" }
            for ((width, height, scale) in listOf(Triple(296, 288, 1f), Triple(353, 414, 1.3f))) {
                val selected = selectLoreCardText(measure(width, scale, body, heading), height)
                assertTrue("$heading needs ${selected.heightPx}px at width=$width scale=$scale", selected.heightPx <= height)
            }
        }
    }

    @Test
    fun `spacing is reclaimed before shrinking the heading and empty bodies add no gap`() {
        val candidates = measure(width = 353)
        val selected = selectLoreCardText(candidates, candidates[1].heightPx)
        assertEquals(28, selected.spec.headingSp)
        assertTrue(measure(width = 353, body = null)[1].heightPx < candidates[1].heightPx)
    }

    @Test
    fun `impossible content is detected without reducing readable typography further`() {
        val candidates = measure(width = 280, body = explanation.repeat(10))
        val selected = selectLoreCardText(candidates, heightPx = 272)
        assertTrue(selected.heightPx > 272)
        assertEquals(24, selected.spec.headingSp)
        // Nothing fits, so the card scrolls its text instead of cutting it off.
        assertTrue(selected.overflows)
    }

    @Test
    fun `text only scrolls when even the smallest size is too tall`() {
        val candidates = measure(width = 353)
        assertFalse(selectLoreCardText(candidates, candidates.first().heightPx).overflows)
        assertFalse(selectLoreCardText(candidates, candidates.last().heightPx).overflows)
        assertTrue(selectLoreCardText(candidates, candidates.last().heightPx - 1).overflows)
    }

    @Test
    fun `tall cards give short and medium questions the larger heading, long ones keep today's sizes`() {
        // A medium question on a 412x915 phone card (372dp wide, about 540dp of room for text).
        val tall = measure(width = 372, heading = "Why do familiar places feel different when we return?")
        assertTrue("fits a phone card", tall.first().heightPx <= 540)
        assertEquals(32, selectLoreCardText(tall, heightPx = 540).spec.headingSp)
        val long = "Why did a single faulty relay in a quiet substation leave fifty million people in the dark for days, and what did it teach the engineers who rebuilt the grid afterwards?"
        val candidates = measure(width = 300, heading = long)
        assertTrue("five lines at 32sp", candidates.first().headingLines > 4)
        assertEquals(28, selectLoreCardText(candidates, heightPx = 2_000).spec.headingSp)
        // Short rooms pick what they always did.
        assertEquals(selectLoreCardText(measure(width = 353).drop(1), heightPx = 250).spec, selectLoreCardText(measure(width = 353), heightPx = 250).spec)
    }

    @Test
    fun `episode length rounds up to whole minutes and is left out when unknown`() {
        assertEquals(60, loreDurationMinutes(3_600))
        assertEquals(1, loreDurationMinutes(1))
        assertEquals(2, loreDurationMinutes(61))
        assertEquals(null, loreDurationMinutes(0))
        assertEquals(null, loreDurationMinutes(-5))
    }
}
