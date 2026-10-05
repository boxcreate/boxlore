package cx.aswin.boxlore.feature.explore

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.theme.buildBoxLoreTypography
import org.json.JSONArray
import org.junit.Assert.assertEquals
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
        val selected = selectLoreCardText(candidates, heightPx = 430)
        assertTrue("Scaled full card needs ${selected.heightPx}px", selected.heightPx <= 430)
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
            for ((width, height, scale) in listOf(Triple(296, 288, 1f), Triple(353, 430, 1.3f))) {
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
        assertTrue(measure(width = 353, body = null).first().heightPx < candidates.first().heightPx)
    }

    @Test
    fun `impossible content is detected without reducing readable typography further`() {
        val candidates = measure(width = 280, body = explanation.repeat(10))
        val selected = selectLoreCardText(candidates, heightPx = 272)
        assertTrue(selected.heightPx > 272)
        assertEquals(24, selected.spec.headingSp)
    }
}
