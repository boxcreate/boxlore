package cx.aswin.boxlore.feature.onboarding

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.prefs.AppLanguage
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The language question's panel for Welcome to take over from ([from]), and what to call once it has. */
internal class WelcomeHandoff(val from: OpeningHandoff?, val onDone: () -> Unit) {
    companion object {
        val None = WelcomeHandoff(null) {}
    }
}

/**
 * Welcome taking over from the language question. Until the picked language is in effect (the
 * activity is recreated for it) Welcome holds the question's last frame: its panel height and
 * scroll, its greeting, and nothing of Welcome's own below. Then the panel settles at Welcome's
 * height (and the page at its top) on [ExpressiveMotion.FormalSpring], the greeting fades out before
 * the covers come in, and the actions and Echo rise in. [panel] is null beside the panel, where both
 * screens share the same width.
 */
internal class OpeningHandoffMotion(from: OpeningHandoff, panelStart: Float?, scrollStart: Float) {
    val greeting = Animatable(1f)
    val covers = Animatable(0f)
    val content = Animatable(0f)
    val panel: Animatable<Float, AnimationVector1D>? = panelStart?.let { Animatable(it) }

    /** The page's offset in px: where the question had scrolled, settling at 0. */
    val shift = Animatable(-scrollStart)

    /** How the greeting stood at the tap. */
    val greetingFrame: HandoffGreetingFrame = from.greeting

    /** Welcome's own panel height, in px, once the panel has been measured. */
    var natural by mutableFloatStateOf(Float.NaN)

    /** False while Welcome still holds the question's last frame. */
    var started by mutableStateOf(false)
}

/**
 * The hand-off's motion for [from], starting the panel at [startPx] and the page at [scrollPx], or null
 * when there is nothing to take over. It starts once [ready] (the picked language is in effect, or
 * [HandoffHoldMillis] have passed), and [onDone] runs once everything has settled.
 */
@Composable
internal fun rememberOpeningHandoffMotion(from: OpeningHandoff?, startPx: Float, scrollPx: Float, ready: Boolean, onDone: () -> Unit): OpeningHandoffMotion? {
    val done by rememberUpdatedState(onDone)
    val motion = remember(from) {
        from?.let { OpeningHandoffMotion(it, if (it.twoPane) null else startPx, scrollPx) }
    } ?: return null
    var waited by remember(motion) { mutableStateOf(false) }
    LaunchedEffect(motion) {
        delay(HandoffHoldMillis)
        waited = true
    }
    val measured = motion.panel == null || !motion.natural.isNaN()
    LaunchedEffect(motion, measured, ready || waited) {
        val panel = motion.panel
        if (!(ready || waited) || !measured) return@LaunchedEffect
        motion.started = true
        val parts = listOf(
            launch { motion.greeting.animateTo(0f, tween(HandoffGreetingFadeMillis)) },
            launch { motion.covers.animateTo(1f, tween(HandoffCoversMillis, delayMillis = HandoffCoversDelayMillis)) },
            launch { motion.content.animateTo(1f, tween(HandoffContentMillis, delayMillis = HandoffContentDelayMillis, easing = WelcomeEntryTiming.Easing)) },
            launch { motion.shift.animateTo(0f, ExpressiveMotion.FormalSpring) },
        )
        // Welcome's own height can still change while the panel settles or the other parts finish (a
        // resize); follow it until the panel matches, so the hand-off never ends at an old height.
        do {
            while (panel != null && panel.value != motion.natural) panel.animateTo(motion.natural, ExpressiveMotion.FormalSpring)
            parts.forEach { it.join() }
        } while (panel != null && panel.value != motion.natural)
        done()
    }
    return motion
}

/** Whether [language], just picked, is the one the screen now shows: the phone's own, or a match. */
@Composable
internal fun appLanguageInEffect(language: AppLanguage): Boolean {
    if (language == AppLanguage.SYSTEM) return true
    val shown = LocalConfiguration.current.locales[0]?.language
    return Locale.forLanguageTag(language.tag).language == shown
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

/** Welcome's page starts where the question had scrolled to and settles at its top. */
internal fun Modifier.openingPageShift(motion: OpeningHandoffMotion?): Modifier {
    if (motion == null) return this
    return graphicsLayer { translationY = motion.shift.value }
}

/** Welcome's own parts below the panel rise in with the hand-off; nothing changes without one. */
internal fun Modifier.openingHandoffEntry(motion: OpeningHandoffMotion?): Modifier {
    if (motion == null) return this
    return graphicsLayer {
        val entry = motion.content.value
        alpha = entry
        translationY = HandoffRise.toPx() * (1f - entry)
    }
}

/**
 * The greeting the question showed at the tap, held still where it was (the same word, fade and
 * rise, over the same foot of the panel) while Welcome takes over the panel; it fades out with the
 * hand-off before the cover reel comes in. Decorative, like the original.
 */
@Composable
internal fun HandoffGreeting(motion: OpeningHandoffMotion, greetingSize: Float, color: Color, welcomeFoot: Dp, modifier: Modifier = Modifier) {
    val frame = motion.greetingFrame
    val (style, promptStyle) = languageGreetingStyles(greetingSize)
    val still = Modifier.graphicsLayer {
        alpha = frame.alpha * motion.greeting.value
        translationY = size.height * frame.shift
    }
    Column(
        modifier.fillMaxWidth().offset(y = welcomeFoot - frame.footDp.dp).testTag("handoff_greeting").clearAndSetSemantics {},
    ) {
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            Text(stringResource(frame.language.greetingRes()), style = style, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, modifier = still)
        }
        Box(Modifier.fillMaxWidth().clipToBounds()) {
            Text(stringResource(frame.language.promptRes()), style = promptStyle, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = still)
        }
    }
}

/** The question's greeting on show now ([clock]'s time), for Welcome to take over where it stands. */
internal fun greetingFrame(clock: WelcomeMotionClock, languages: List<AppLanguage>, footDp: Float): HandoffGreetingFrame {
    val frame = LanguageGreetingMotion.frame(clock.elapsedMillis.value, languages.size, clock.animationsDisabled.value)
    return HandoffGreetingFrame(languages[frame.index], frame.alpha, frame.translationFraction, footDp)
}

/** Welcome's invitation waits while the hand-off holds the question's last frame, then enters as usual. */
internal fun Modifier.openingInvitationHold(motion: OpeningHandoffMotion?): Modifier {
    if (motion == null) return this
    return graphicsLayer { alpha = if (motion.started) 1f else 0f }
}

/** True when the system's animations are off ("Remove animations"); hand-offs then change in one step. */
@Composable
internal fun rememberAnimationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** Welcome waits this long at most for the picked language before it takes over anyway. */
internal const val HandoffHoldMillis = 2_500L

/** The greeting is gone before the covers pass a third of their fade, so text never sits over art. */
private const val HandoffGreetingFadeMillis = 120
private const val HandoffCoversDelayMillis = 100
private const val HandoffCoversMillis = 300
private const val HandoffContentDelayMillis = 160
private const val HandoffContentMillis = 420
private val HandoffRise = 16.dp
