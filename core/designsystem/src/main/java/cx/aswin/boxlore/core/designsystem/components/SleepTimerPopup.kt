package cx.aswin.boxlore.core.designsystem.components

import android.animation.ValueAnimator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.SleepTimerConstants
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** A single selectable duration option in the sleep timer popup. 999 means "End of episode". */
data class SleepTimerOption(val label: String, val minutes: Int)

enum class SleepTimerPopupDismissReason {
    Manual,
    Timeout,
    Confirmation,

    /** The listener took the offer to play on the watch instead, so this phone's timer no longer matters. */
    PlayedOnWatch
}

val DefaultSleepTimerOptions = listOf(
    SleepTimerOption("30m", 30),
    SleepTimerOption("45m", 45),
    SleepTimerOption("1h", 60),
    SleepTimerOption("2h", 120),
    SleepTimerOption("End of episode", SleepTimerConstants.END_OF_EPISODE_MINUTES)
)

/**
 * The late-night sleep timer nudge, "Moonrise": a card in the theme's colours that drops in from
 * the top with a night-sky band (a slowly turning nine-scalloped moon and twinkling stars), the
 * question, the times as a connected button group and End of episode under it. Picking one sets
 * the timer at once: the buttons fold away, the moon sets behind the band and a seafoam badge
 * rises in its place with the time picked, beside "Pausing in 45 min. Good night." Then the card
 * leaves. It hides itself after [autoHideMillis] untouched. With animations off every step shows
 * its final state.
 *
 * [onPlayOnWatch] is set when this phone has just taken over from a playing watch: the card then
 * also says the watch was paused and offers it back, and waits five seconds longer, since it asks
 * two things at once.
 */
@Composable
fun SleepTimerPopup(
    visible: Boolean,
    modifier: Modifier = Modifier,
    options: List<SleepTimerOption> = DefaultSleepTimerOptions,
    autoHideMillis: Long = 8_000L,
    onPlayOnWatch: (() -> Unit)? = null,
    onSelectDuration: (Int) -> Unit,
    onDismiss: (SleepTimerPopupDismissReason) -> Unit
) {
    val dismiss by rememberUpdatedState(onDismiss)
    val select by rememberUpdatedState(onSelectDuration)
    val shown by rememberUpdatedState(visible)

    AnimatedVisibility(
        visible = visible,
        enter = EnterTransition.None,
        exit = fadeOut(tween(SleepPromptMotion.EXIT_MILLIS)) +
            slideOutVertically(tween(SleepPromptMotion.EXIT_MILLIS)) { -it / 5 } +
            scaleOut(tween(SleepPromptMotion.EXIT_MILLIS), targetScale = 0.96f),
        modifier = modifier
    ) {
        // One showing's own state: it leaves with the prompt, so the next showing starts afresh,
        // and it survives a configuration change while shown, so a rotation keeps the confirmation.
        var picked by rememberSaveable { mutableStateOf<Int?>(null) }
        var confirmed by rememberSaveable { mutableStateOf(false) }
        val clocks = rememberSleepPromptClocks(picked)
        LaunchedEffect(picked) {
            if (picked == null) {
                delay(SleepPromptMotion.autoHideMillis(autoHideMillis, offersWatch = onPlayOnWatch != null))
                // Read now, not when the timer started: a prompt already on its way out times out silently.
                if (shown) dismiss(SleepTimerPopupDismissReason.Timeout)
            } else if (!confirmed) {
                delay(SleepPromptMotion.CONFIRM_HOLD_MILLIS)
                confirmed = true
                dismiss(SleepTimerPopupDismissReason.Confirmation)
            }
        }
        // Hidden from outside while confirming: the confirmation still counts, once.
        LaunchedEffect(visible) {
            if (!visible && picked != null && !confirmed) {
                confirmed = true
                dismiss(SleepTimerPopupDismissReason.Confirmation)
            }
        }
        SleepPromptCard(
            options = options,
            picked = picked,
            clocks = clocks,
            onPlayOnWatch = onPlayOnWatch?.let { play ->
                {
                    if (picked == null) {
                        play()
                        dismiss(SleepTimerPopupDismissReason.PlayedOnWatch)
                    }
                }
            },
            onPick = { minutes ->
                if (picked == null) {
                    picked = minutes
                    select(minutes)
                }
            },
            onDismiss = {
                // Before a pick it is a dismissal; after one it only ends the confirmation early.
                when {
                    picked == null -> dismiss(SleepTimerPopupDismissReason.Manual)
                    !confirmed -> {
                        confirmed = true
                        dismiss(SleepTimerPopupDismissReason.Confirmation)
                    }
                }
            },
        )
    }
}

/** The prompt's two clocks, in milliseconds: its entrance, and the confirmation after a pick. */
private class SleepPromptClocks(val enter: State<Float>, val confirm: State<Float>, val still: Boolean)

@Composable
private fun rememberSleepPromptClocks(picked: Int?): SleepPromptClocks {
    val still = remember { !ValueAnimator.areAnimatorsEnabled() }
    val enter = remember { Animatable(if (still) SleepPromptMotion.ENTER_MILLIS else 0f) }
    // Nothing to confirm until a pick: the clock starts at 0 even with animations off.
    val confirm = remember { Animatable(if (picked != null) SleepPromptMotion.CONFIRM_MILLIS else 0f) }
    LaunchedEffect(Unit) {
        if (!still) enter.animateTo(SleepPromptMotion.ENTER_MILLIS, tween(SleepPromptMotion.ENTER_MILLIS.toInt(), easing = LinearEasing))
    }
    LaunchedEffect(picked) {
        if (picked == null || confirm.value >= SleepPromptMotion.CONFIRM_MILLIS) return@LaunchedEffect
        if (still) {
            confirm.snapTo(SleepPromptMotion.CONFIRM_MILLIS)
        } else {
            confirm.animateTo(SleepPromptMotion.CONFIRM_MILLIS, tween(SleepPromptMotion.CONFIRM_MILLIS.toInt(), easing = LinearEasing))
        }
    }
    return remember(enter, confirm) { SleepPromptClocks(enter.asState(), confirm.asState(), still) }
}

@Composable
private fun SleepPromptCard(
    options: List<SleepTimerOption>,
    picked: Int?,
    clocks: SleepPromptClocks,
    onPlayOnWatch: (() -> Unit)?,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 8.dp,
        // Capped first, then filled: a phone gets the full width, a wide window a card-sized prompt.
        modifier = Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .graphicsLayer {
                val pose = SleepPromptMotion.card(clocks.enter.value)
                alpha = pose.alpha
                translationY = pose.offsetDp * density
                scaleX = pose.scale
                scaleY = pose.scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
            },
    ) {
        Column(Modifier.padding(12.dp)) {
            NightBand(picked = picked, clocks = clocks, onDismiss = onDismiss)
            SleepPromptChoices(
                options = options,
                picked = picked,
                clocks = clocks,
                onPlayOnWatch = onPlayOnWatch,
                onPick = onPick,
                modifier = Modifier.collapseBy { SleepPromptMotion.choicesHeight(clocks.confirm.value) },
            )
        }
    }
}

/** The night sky: stars, the moon (or, after a pick, the time badge), the question or the answer, and Dismiss. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NightBand(picked: Int?, clocks: SleepPromptClocks, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spin = rememberSpin(clocks.still)
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.primaryContainer),
    ) {
        Stars(
            color = colors.onPrimaryContainer,
            fast = picked != null,
            still = clocks.still,
            modifier = Modifier.matchParentSize(),
        )
        val cookie = MaterialShapes.Cookie9Sided.toShape()
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = 14.dp)
                .size(64.dp)
                .graphicsLayer {
                    val enter = SleepPromptMotion.moon(clocks.enter.value)
                    alpha = enter.alpha
                    scaleX = enter.scale
                    scaleY = enter.scale
                    rotationZ = enter.rotation
                    translationY = SleepPromptMotion.moonSet(clocks.confirm.value) * density
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(64.dp).graphicsLayer { rotationZ = spin.value }.background(colors.primary, cookie))
            Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(30.dp))
        }
        if (picked != null) {
            TimeBadge(
                minutes = picked,
                cookie = cookie,
                spin = spin,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .graphicsLayer {
                        val rise = SleepPromptMotion.badge(clocks.confirm.value)
                        translationY = rise.offsetDp * density
                        rotationZ = rise.rotation
                    },
            )
        }
        Column(
            Modifier
                .padding(start = 92.dp, end = 48.dp, top = 16.dp, bottom = 16.dp)
                .graphicsLayer { alpha = SleepPromptMotion.questionAlpha(clocks.confirm.value) }
                // Faded out after a pick: screen readers hear the answer only.
                .then(if (picked != null) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            Text(
                stringResource(R.string.sleep_prompt_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.sleep_prompt_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (picked != null) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 92.dp, end = 48.dp)
                    .graphicsLayer {
                        val answer = SleepPromptMotion.answer(clocks.confirm.value)
                        alpha = answer.alpha
                        translationY = answer.offsetDp * density
                    }
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Text(
                    pausingText(picked),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.sleep_prompt_good_night),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimaryContainer,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(40.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.sleep_prompt_dismiss), tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
        }
    }
}

/** The picked time on a seafoam scalloped badge: "45 min", "1 hr", or the end-of-episode mark. */
@Composable
private fun TimeBadge(minutes: Int, cookie: androidx.compose.ui.graphics.Shape, spin: State<Float>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val badge = sleepTimerBadge(minutes)
    Box(modifier.size(64.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
        Box(Modifier.size(64.dp).graphicsLayer { rotationZ = spin.value }.background(colors.tertiary, cookie))
        when (badge) {
            SleepTimerBadge.EndOfEpisode -> Icon(Icons.Rounded.SkipNext, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(26.dp))
            is SleepTimerBadge.Time -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(badge.amount.toString(), color = colors.onTertiary, fontSize = 20.sp, lineHeight = 20.sp, style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(if (badge.hours) R.string.sleep_prompt_unit_hours else R.string.sleep_prompt_unit_minutes),
                    color = colors.onTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

/** The times as a connected button group, End of episode under it; each pops in, and the picked one fills. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SleepPromptChoices(
    options: List<SleepTimerOption>,
    picked: Int?,
    clocks: SleepPromptClocks,
    onPlayOnWatch: (() -> Unit)?,
    onPick: (Int) -> Unit,
    modifier: Modifier,
) {
    val times = options.filterNot { it.minutes == SleepTimerConstants.END_OF_EPISODE_MINUTES }
    val endOfEpisode = options.firstOrNull { it.minutes == SleepTimerConstants.END_OF_EPISODE_MINUTES }
    Column(
        modifier
            .graphicsLayer { alpha = SleepPromptMotion.choicesAlpha(clocks.confirm.value) }
            // Folded away after a pick: the old times are no longer reachable.
            .then(if (picked != null) Modifier.clearAndSetSemantics {} else Modifier),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            times.forEachIndexed { index, option ->
                TimeButton(
                    option = option,
                    position = when (index) {
                        0 -> GroupPosition.Leading
                        times.lastIndex -> GroupPosition.Trailing
                        else -> GroupPosition.Middle
                    },
                    picked = picked == option.minutes,
                    pop = { SleepPromptMotion.buttonPop(clocks.enter.value, index) },
                    onClick = { onPick(option.minutes) },
                )
            }
        }
        if (endOfEpisode != null) {
            Spacer(Modifier.height(8.dp))
            val colors = MaterialTheme.colorScheme
            val chosen = picked == endOfEpisode.minutes
            val container by animateColorAsState(if (chosen) colors.primary else colors.primaryContainer, label = "endContainer")
            val content by animateColorAsState(if (chosen) colors.onPrimary else colors.onPrimaryContainer, label = "endContent")
            Button(
                onClick = { onPick(endOfEpisode.minutes) },
                shapes = ButtonShapes(RoundedCornerShape(24.dp), RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .graphicsLayer {
                        val pop = SleepPromptMotion.endPop(clocks.enter.value)
                        alpha = pop.alpha
                        translationY = pop.offsetDp * density
                    },
            ) {
                Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(endOfEpisode.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (onPlayOnWatch != null) {
            // The phone has just taken over from the watch: the second thing this card asks.
            Text(
                stringResource(R.string.watch_notice_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
            )
            PlayOnWatchInsteadButton(onClick = onPlayOnWatch)
        }
    }
}

private enum class GroupPosition { Leading, Middle, Trailing }

/** One time in the group: connected corners at rest, round while pressed or once picked; it widens as it is pressed and picked. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun androidx.compose.foundation.layout.RowScope.TimeButton(
    option: SleepTimerOption,
    position: GroupPosition,
    picked: Boolean,
    pop: () -> SleepPromptMotion.Pose,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val weight by animateFloatAsState(
        targetValue = when {
            picked -> 2f
            pressed -> 1.4f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "timeWeight",
    )
    val container by animateColorAsState(if (picked) colors.primary else colors.secondaryContainer, label = "timeContainer")
    val content by animateColorAsState(if (picked) colors.onPrimary else colors.onSecondaryContainer, label = "timeContent")
    val outer = 24.dp
    val inner = 8.dp
    val rest = when (position) {
        GroupPosition.Leading -> RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
        GroupPosition.Middle -> RoundedCornerShape(inner)
        GroupPosition.Trailing -> RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
    }
    Button(
        onClick = onClick,
        shapes = ButtonShapes(if (picked) RoundedCornerShape(outer) else rest, RoundedCornerShape(outer)),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        contentPadding = PaddingValues(horizontal = 4.dp),
        interactionSource = interaction,
        modifier = Modifier
            .weight(weight)
            .height(48.dp)
            .graphicsLayer {
                val p = pop()
                alpha = p.alpha
                scaleX = p.scale
                scaleY = p.scale
            },
    ) {
        Text(option.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** Three four-point stars at the band's open end, twinkling in turn (faster once a time is picked). */
@Composable
private fun Stars(color: Color, fast: Boolean, still: Boolean, modifier: Modifier) {
    val twinkle = if (still) {
        remember { mutableStateOf(0.5f) }
    } else {
        val transition = rememberInfiniteTransition(label = "stars")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(if (fast) 900 else 2_400, easing = LinearEasing), RepeatMode.Restart),
            label = "twinkle",
        )
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val unit = 1.dp.toPx()
        SleepPromptMotion.Stars.forEachIndexed { i, star ->
            val glow = SleepPromptMotion.twinkle(twinkle.value, i)
            val centre = Offset(size.width - star.fromEnd * unit, size.height * star.down)
            val r = star.radius * unit * glow.scale
            val path = Path().apply {
                moveTo(centre.x, centre.y - r)
                quadraticTo(centre.x, centre.y, centre.x + r, centre.y)
                quadraticTo(centre.x, centre.y, centre.x, centre.y + r)
                quadraticTo(centre.x, centre.y, centre.x - r, centre.y)
                quadraticTo(centre.x, centre.y, centre.x, centre.y - r)
                close()
            }
            drawPath(path, color, alpha = glow.alpha)
        }
    }
}

/** The moon's and the badge's slow turn: one round every 24 seconds; still with animations off. */
@Composable
private fun rememberSpin(still: Boolean): State<Float> {
    if (still) return remember { mutableStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "spin")
    return transition.animateFloat(0f, 360f, infiniteRepeatable(tween(SleepPromptMotion.SPIN_MILLIS, easing = LinearEasing)), label = "spin")
}

/** Lays out [fraction] of the content's height (0 to 1), so the card folds up as the choices go. */
private fun Modifier.collapseBy(fraction: () -> Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * fraction().coerceIn(0f, 1f)).roundToInt()
    layout(placeable.width, height) { placeable.place(0, 0) }
}

@Composable
private fun pausingText(minutes: Int): String = when (val badge = sleepTimerBadge(minutes)) {
    SleepTimerBadge.EndOfEpisode -> stringResource(R.string.sleep_prompt_pausing_episode)
    is SleepTimerBadge.Time -> stringResource(
        if (badge.hours) R.string.sleep_prompt_pausing_hours else R.string.sleep_prompt_pausing_minutes,
        badge.amount,
    )
}

/** What the badge shows for a picked time: whole hours as hours, anything else as minutes, or the end of the episode. */
internal sealed interface SleepTimerBadge {
    data class Time(val amount: Int, val hours: Boolean) : SleepTimerBadge
    data object EndOfEpisode : SleepTimerBadge
}

internal fun sleepTimerBadge(minutes: Int): SleepTimerBadge = when {
    minutes == SleepTimerConstants.END_OF_EPISODE_MINUTES -> SleepTimerBadge.EndOfEpisode
    minutes >= 60 && minutes % 60 == 0 -> SleepTimerBadge.Time(minutes / 60, hours = true)
    else -> SleepTimerBadge.Time(minutes, hours = false)
}
