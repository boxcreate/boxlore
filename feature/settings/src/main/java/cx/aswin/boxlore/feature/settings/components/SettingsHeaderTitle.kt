package cx.aswin.boxlore.feature.settings.components

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.lerp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal data class SettingsHeaderTitlePresentation(
    val style: TextStyle,
    val maxLines: Int = 2,
)

/** Keep wrapping available throughout collapse so long page names remain readable. */
internal fun settingsHeaderTitlePresentation(
    typography: Typography,
    collapsedFraction: Float,
    expanded: Boolean = true,
): SettingsHeaderTitlePresentation {
    val fraction = if (collapsedFraction.isFinite()) collapsedFraction.coerceIn(0f, 1f) else 0f
    val compactStyle = typography.titleLarge.copy(fontWeight = GoogleSansWeight.semiBold)
    return SettingsHeaderTitlePresentation(
        style = if (expanded) {
            lerp(
                start = typography.displaySmall.copy(fontWeight = GoogleSansWeight.bold),
                stop = compactStyle,
                fraction = fraction,
            )
        } else {
            compactStyle
        },
    )
}
