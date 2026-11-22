package cx.aswin.boxlore.feature.onboarding

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.prefs.AppLanguage
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The language question's panel for Welcome to take over from ([from]), and what to call once it has. */
internal class WelcomeHandoff(val from: OpeningHandoff?, val onDone: () -> Unit) {
    companion object {
        val None = WelcomeHandoff(null) {}
    }
}

/**
 * Welcome taking over from the language question: the panel starts at the question's height and
 * settles at Welcome's on [ExpressiveMotion.FormalSpring], while the greeting fades out as the cover
 * reel comes in. [panel] is null beside the panel, where both screens share the same width.
 */
internal class OpeningHandoffMotion(
    val greeting: Animatable<Float, AnimationVector1D>,
    val panel: Animatable<Float, AnimationVector1D>?,
) {
    /** Welcome's own panel height, in px, once the panel has been measured. */
    var natural by mutableFloatStateOf(Float.NaN)
}

/**
 * The hand-off's motion for [from], starting the panel at [startPx], or null when there is nothing to
 * take over. [onDone] runs once the panel has settled and the greeting has gone.
 */
@Composable
internal fun rememberOpeningHandoffMotion(from: OpeningHandoff?, startPx: Float, onDone: () -> Unit): OpeningHandoffMotion? {
    val done by rememberUpdatedState(onDone)
    val motion = remember(from) {
        from?.let { OpeningHandoffMotion(Animatable(1f), if (it.twoPane) null else Animatable(startPx)) }
    } ?: return null
    LaunchedEffect(motion, motion.natural) {
        val panel = motion.panel
        if (panel != null && motion.natural.isNaN()) return@LaunchedEffect
        val fade = launch { motion.greeting.animateTo(0f, tween(OpeningGreetingFadeMillis)) }
        panel?.animateTo(motion.natural, ExpressiveMotion.FormalSpring)
        fade.join()
        done()
    }
    return motion
}

/**
 * Sizes the panel to the hand-off's animated height instead of its own, read in the layout phase;
 * the panel's own height becomes the target. Nothing changes without a panel motion.
 */
internal fun Modifier.openingPanelMorph(motion: OpeningHandoffMotion?): Modifier {
    val panel = motion?.panel ?: return this
    return layout { measurable, constraints ->
        val natural = measurable.maxIntrinsicHeight(constraints.maxWidth).toFloat()
        if (motion.natural != natural) motion.natural = natural
        val height = panel.value.roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
}

/**
 * The picked [language]'s greeting, held still where the question showed it, while Welcome takes
 * over the panel; it fades out with [alpha] as the cover reel comes in. Decorative, like the original.
 */
@Composable
internal fun HandoffGreeting(language: AppLanguage, greetingSize: Float, color: Color, alpha: () -> Float, modifier: Modifier = Modifier) {
    val (style, promptStyle) = languageGreetingStyles(greetingSize)
    Column(modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha() }.testTag("handoff_greeting").clearAndSetSemantics {}) {
        Text(stringResource(language.greetingRes()), style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip)
        Text(stringResource(language.promptRes()), style = promptStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** True when the system's animations are off ("Remove animations"); hand-offs then change in one step. */
@Composable
internal fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** The greeting gives way quickly, as the cover reel's own entrance starts. */
private const val OpeningGreetingFadeMillis = 200
