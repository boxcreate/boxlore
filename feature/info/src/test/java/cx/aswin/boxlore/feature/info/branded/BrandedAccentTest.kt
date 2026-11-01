package cx.aswin.boxlore.feature.info.branded

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

internal class BrandedAccentTest {
    data class Case(val page: BrandedShowPage, val dark: Boolean) {
        override fun toString() = "${page::class.simpleName} ${if (dark) "dark" else "light"}"
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `Subscribe takes the page's accent, like Play and the other filled buttons`(case: Case) {
        // Regression: Crime Junkie's Subscribe was ink while its Play buttons and genre chip were violet.
        val style = case.page.style(if (case.dark) darkColorScheme() else lightColorScheme(), case.dark)
        assertEquals(style.colors.primary, style.toolbar.subscribeContainer, "$case Subscribe")
        assertEquals(style.colors.onPrimary, style.toolbar.subscribeContent, "$case Subscribe label")
    }

    companion object {
        @JvmStatic
        fun cases(): List<Case> = BrandedShowPages.all.flatMap { page -> listOf(Case(page, dark = false), Case(page, dark = true)) }
    }
}
