package cx.aswin.boxlore.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.rememberGoogleSansFamily

/**
 * Listening history's text at the boards' sizes and weights. The theme's styles carry their weight
 * in their font (Google Sans Flex at a set weight), so a copy with another fontWeight still draws the
 * old weight; this uses the font at [weight]. Lines sit in the middle of their [line] height, as the
 * boards' do.
 */
@Composable
internal fun historyTextStyle(size: Float, line: Float? = null, weight: FontWeight = GoogleSansWeight.regular, tracking: Float = 0f): TextStyle = TextStyle(
    fontFamily = rememberGoogleSansFamily(weight.weight),
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line?.sp ?: TextUnit.Unspecified,
    letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
