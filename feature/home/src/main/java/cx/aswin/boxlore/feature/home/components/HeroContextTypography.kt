package cx.aswin.boxlore.feature.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** Rendering and measurement use the same type styles, including native font padding. */
internal object HeroContextTypography {
    @Composable
    fun label() = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp)

    @Composable
    fun duration(wide: Boolean) = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp, lineHeight = if (wide) 30.sp else 34.sp, fontWeight = GoogleSansWeight.bold)

    @Composable
    fun durationCaption() = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp, lineHeight = 20.sp)

    @Composable
    fun action() = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = GoogleSansWeight.regular)

    @Composable
    fun rank(wide: Boolean) = MaterialTheme.typography.displayMedium.copy(fontSize = 54.sp, lineHeight = if (wide) 44.sp else 56.sp, fontWeight = GoogleSansWeight.bold, letterSpacing = (-2).sp)

    @Composable
    fun market() = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, lineHeight = 18.sp)

    @Composable
    fun trendingLabel() = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp)

    @Composable
    fun genre() = MaterialTheme.typography.titleMedium.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = GoogleSansWeight.bold)
}
