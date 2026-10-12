package cx.aswin.boxlore.feature.player.v2

import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

internal const val MINIMAL_EPISODE_TITLE_TAG = "minimalEpisodeTitle"

/** Compose's Auto uses light hyphenation; titles need the platform's full mode. */
@Composable
internal fun MinimalEpisodeTitle(title: String, color: Color, style: TextStyle, modifier: Modifier) {
    val typeface = LocalFontFamilyResolver.current.resolve(style.fontFamily, style.fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal).value as Typeface
    val density = LocalDensity.current
    val minimum = with(density) { 28.sp.toPx().roundToInt() }
    val maximum = with(density) { 40.sp.toPx().roundToInt() }
    val step = with(density) { 1.sp.toPx().roundToInt().coerceAtLeast(1) }
    val locales = LocalConfiguration.current.locales
    AndroidView(
        modifier = modifier.semantics { text = AnnotatedString(title) },
        factory = { context ->
            TextView(context).apply {
                tag = MINIMAL_EPISODE_TITLE_TAG
                setPadding(0, 0, 0, 0)
                includeFontPadding = false
                maxLines = 4
                ellipsize = TextUtils.TruncateAt.END
                setLineSpacing(0f, 1.14f)
                breakStrategy = LineBreaker.BREAK_STRATEGY_HIGH_QUALITY
                hyphenationFrequency = if (Build.VERSION.SDK_INT >= 33) Layout.HYPHENATION_FREQUENCY_FULL_FAST else Layout.HYPHENATION_FREQUENCY_FULL
                textDirection = View.TEXT_DIRECTION_FIRST_STRONG
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                // The Compose node owns the original title and its full-title action.
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setTextSize(TypedValue.COMPLEX_UNIT_PX, maximum.toFloat())
                setAutoSizeTextTypeUniformWithConfiguration(minimum, maximum, step, TypedValue.COMPLEX_UNIT_PX)
            }
        },
        update = { view ->
            if (view.text.toString() != title) view.text = title
            if (view.typeface != typeface) view.typeface = typeface
            view.setTextColor(color.toArgb())
            view.textLocales = locales
            view.letterSpacing = -0.7f / 40f
            if (view.autoSizeMinTextSize != minimum || view.autoSizeMaxTextSize != maximum || view.autoSizeStepGranularity != step) {
                view.setAutoSizeTextTypeUniformWithConfiguration(minimum, maximum, step, TypedValue.COMPLEX_UNIT_PX)
            }
        },
    )
}
