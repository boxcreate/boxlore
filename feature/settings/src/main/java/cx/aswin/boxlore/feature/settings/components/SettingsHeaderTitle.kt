package cx.aswin.boxlore.feature.settings.components

import androidx.compose.material3.Typography
import cx.aswin.boxlore.core.designsystem.component.BoxloreLargeTitlePresentation
import cx.aswin.boxlore.core.designsystem.component.boxloreLargeTitlePresentation

internal typealias SettingsHeaderTitlePresentation = BoxloreLargeTitlePresentation

/**
 * Settings' page titles: the shared large title in `displaySmall`, wrapping to two lines throughout
 * the collapse and in the collapsed row too, so long page names remain readable.
 */
internal fun settingsHeaderTitlePresentation(
    typography: Typography,
    collapsedFraction: Float,
    expanded: Boolean = true,
): SettingsHeaderTitlePresentation = boxloreLargeTitlePresentation(
    typography = typography,
    collapsedFraction = collapsedFraction,
    expanded = expanded,
    largeStyle = typography.displaySmall,
    collapsedMaxLines = 2,
)
