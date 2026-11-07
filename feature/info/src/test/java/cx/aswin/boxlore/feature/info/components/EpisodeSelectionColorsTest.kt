package cx.aswin.boxlore.feature.info.components

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.feature.info.branded.BrandedShowPage
import cx.aswin.boxlore.feature.info.branded.BrandedShowPages
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class EpisodeSelectionColorsTest {
    /**
     * Reported: on The Rest Is History a selected episode turned ivory, its grey description almost
     * gone. On every Showcase page, in both themes, a selected card's text stays readable and the
     * card still looks different from an unselected one.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `a selected card on a Showcase page keeps readable text`(case: Case) {
        val scheme = case.page.style(if (case.dark) darkColorScheme() else lightColorScheme(), case.dark).colors
        val selected = brandedSelectionColors(scheme)

        assertTrue(contrast(selected.content, selected.container) >= 4.5f, "Title: ${contrast(selected.content, selected.container)}")
        assertTrue(contrast(scheme.onSurfaceVariant, selected.container) >= 3f, "Description: ${contrast(scheme.onSurfaceVariant, selected.container)}")
        assertTrue(selected.container != scheme.surfaceContainerLow, "Tinted, not the plain card")
        assertTrue(
            contrast(selected.container, scheme.surfaceContainerLow) < contrast(selected.container, scheme.primary),
            "Nearer the card's own surface than the accent: not a block of it",
        )
    }

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }

    data class Case(val page: BrandedShowPage, val dark: Boolean) {
        override fun toString() = "${page::class.simpleName} ${if (dark) "dark" else "light"}"
    }

    companion object {
        @JvmStatic
        fun cases(): List<Case> = BrandedShowPages.all.flatMap { listOf(Case(it, dark = false), Case(it, dark = true)) }
    }
}
