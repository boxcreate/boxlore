package cx.aswin.boxlore.feature.history.insights

import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.history.historyTextStyle

/** Type of the Insights boards (A4–A6): sizes, line heights and weights as drawn. */
internal object InsightsType {
    val label @Composable get() = historyTextStyle(14f, 20f, GoogleSansWeight.semiBold)
    val hero @Composable get() = historyTextStyle(45f, 50f, GoogleSansWeight.semiBold, -0.3f)
    val body16 @Composable get() = historyTextStyle(16f, 22f)
    val body14 @Composable get() = historyTextStyle(14f, 20f)
    val body13 @Composable get() = historyTextStyle(13f, 18f)
    val pill @Composable get() = historyTextStyle(13f, 18f, GoogleSansWeight.semiBold)
    val typeName @Composable get() = historyTextStyle(28f, 32f, GoogleSansWeight.semiBold)
    val title20 @Composable get() = historyTextStyle(20f, 26f, GoogleSansWeight.semiBold)
    val title18 @Composable get() = historyTextStyle(18f, 24f, GoogleSansWeight.semiBold)
    val value24 @Composable get() = historyTextStyle(24f, 28f, GoogleSansWeight.semiBold)
    val rankBadge @Composable get() = historyTextStyle(14f, weight = GoogleSansWeight.bold)
    val badge10 @Composable get() = historyTextStyle(10f, 12f, GoogleSansWeight.semiBold)
}
