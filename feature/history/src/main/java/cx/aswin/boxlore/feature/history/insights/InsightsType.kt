package cx.aswin.boxlore.feature.history.insights

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

/** Type of the Insights boards (A4–A6): sizes, line heights and weights as drawn. */
internal object InsightsType {
    private val base: TextStyle @Composable get() = MaterialTheme.typography.bodyMedium.copy(letterSpacing = 0.sp)
    val label @Composable get() = base.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = GoogleSansWeight.semiBold)
    val hero @Composable get() = base.copy(fontSize = 45.sp, lineHeight = 50.sp, fontWeight = GoogleSansWeight.semiBold, letterSpacing = (-0.3).sp)
    val body16 @Composable get() = base.copy(fontSize = 16.sp, lineHeight = 22.sp)
    val body14 @Composable get() = base.copy(fontSize = 14.sp, lineHeight = 20.sp)
    val body13 @Composable get() = base.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val pill @Composable get() = base.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = GoogleSansWeight.semiBold)
    val typeName @Composable get() = base.copy(fontSize = 28.sp, lineHeight = 32.sp, fontWeight = GoogleSansWeight.semiBold)
    val title20 @Composable get() = base.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = GoogleSansWeight.semiBold)
    val title18 @Composable get() = base.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = GoogleSansWeight.semiBold)
    val value24 @Composable get() = base.copy(fontSize = 24.sp, lineHeight = 28.sp, fontWeight = GoogleSansWeight.semiBold)
    val rankBadge @Composable get() = base.copy(fontSize = 14.sp, fontWeight = GoogleSansWeight.bold)
    val badge10 @Composable get() = base.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = GoogleSansWeight.semiBold)
}
