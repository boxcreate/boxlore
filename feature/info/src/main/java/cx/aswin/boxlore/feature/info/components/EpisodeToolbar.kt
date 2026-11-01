package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.contrastColor
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.LocalBrandedHeaderMotion
import cx.aswin.boxlore.feature.info.branded.LocalBrandedPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Episode Toolbar - M3 Expressive
 * Contains: Search, Sort Toggle, Subscribe Button
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EpisodeToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isSearching: Boolean,
    currentSort: EpisodeSort,
    onSortToggle: () -> Unit,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    accentColor: Color,
    supportsReleaseAutomation: Boolean = true,
    notificationsEnabled: Boolean = false,
    isSystemNotificationsBlocked: Boolean = false,
    onNotificationsToggle: () -> Unit = {},
    autoDownloadEnabled: Boolean = false,
    onAutoDownloadToggle: () -> Unit = {},
    genre: String = "",
    onSearchFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
    subscribeInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val branded = LocalBrandedPage.current?.style?.toolbar
    val subscribe = rememberToolbarSubscribeMoment(
        isSubscribed = isSubscribed,
        moment = LocalBrandedPage.current?.page?.subscribeMoment?.takeIf { branded != null },
    )
    val automationFraction by animateFloatAsState(
        targetValue = if (isSubscribed && supportsReleaseAutomation && !subscribe.revealHeld(isSubscribed)) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isSubscribed) 420f else 560f,
        ),
        label = "subscription_settings_reveal",
    )
    val automationReveal = subscriptionAutomationReveal(automationFraction)
    val automationEnabled = isSubscribed && supportsReleaseAutomation && automationReveal.fullyVisible
    val sortRotation by animateFloatAsState(
        targetValue = if (currentSort == EpisodeSort.NEWEST) 0f else 180f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "sortRotation",
    )
    val colors = subscribeButtonColors(subscribe, branded, accentColor)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubscribeButton(
                subscribe = subscribe,
                isSubscribed = isSubscribed,
                onClick = onSubscribeClick,
                colors = colors,
                compact = compact,
                genre = genre,
                style = branded,
                interactionSource = subscribeInteractionSource,
                modifier = Modifier.weight(1f),
            )
            SubscriptionAutomationSlot(automationReveal, automationEnabled) {
                SubscriptionAutomationToggles(
                    reveal = automationReveal,
                    enabled = automationEnabled,
                    style = branded,
                    notificationsEnabled = notificationsEnabled,
                    notificationsBlocked = notificationsEnabled && isSystemNotificationsBlocked,
                    onNotificationsToggle = onNotificationsToggle,
                    autoDownloadEnabled = autoDownloadEnabled,
                    onAutoDownloadToggle = onAutoDownloadToggle,
                )
            }
            ConnectedToolbarAction(
                icon = Icons.AutoMirrored.Rounded.Sort,
                label = stringResource(R.string.podcast_info_sort),
                onClick = onSortToggle,
                modifier = Modifier.padding(start = 3.dp).graphicsLayer { rotationX = sortRotation },
                style = branded,
            )
            ConnectedToolbarAction(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.podcast_info_search),
                onClick = onSearchFocused,
                modifier = Modifier.padding(start = 3.dp),
                trailing = true,
                style = branded,
            )
        }
    }
}

/** The Subscribe button's phase and, on a Showcase page, the clock of the show's own subscribe moment. */
private class ToolbarSubscribeMoment(
    val phase: SubscriptionButtonPhase,
    val moment: BrandedSubscribeMoment?,
    val clock: Animatable<Float, AnimationVector1D>,
    private val revealReleased: () -> Boolean,
) {
    val playing: Boolean get() = moment != null && phase == SubscriptionButtonPhase.CONFIRMATION

    /** A show's own moment keeps alerts and auto-download back until its reveal time, then they slide in as usual. */
    fun revealHeld(isSubscribed: Boolean): Boolean = moment != null &&
        ((isSubscribed && phase == SubscriptionButtonPhase.SUBSCRIBE) || (playing && !revealReleased()))
}

@Composable
private fun rememberToolbarSubscribeMoment(isSubscribed: Boolean, moment: BrandedSubscribeMoment?): ToolbarSubscribeMoment {
    val phase = rememberSubscriptionButtonPhase(isSubscribed, moment?.durationMillis?.toLong() ?: SUBSCRIPTION_CONFIRMATION_MILLIS)
    val playing = moment != null && phase == SubscriptionButtonPhase.CONFIRMATION
    // A fresh clock for every run, so a second subscribe never starts on the last run's final frame.
    val clock = remember(playing) { Animatable(0f) }
    var revealReleased by remember(phase) { mutableStateOf(false) }
    LaunchedEffect(playing) {
        if (moment == null || !playing) return@LaunchedEffect
        launch {
            // On the moment's own clock, which follows the system animation speed.
            snapshotFlow { clock.value >= moment.revealDelayMillis }.first { it }
            revealReleased = true
        }
        clock.animateTo(moment.durationMillis.toFloat(), tween(moment.durationMillis, easing = LinearEasing))
    }
    // The header may follow the same clock, as Waveform's logo replays its intro.
    val headerMotion = LocalBrandedHeaderMotion.current
    DisposableEffect(headerMotion, clock, playing) {
        if (playing) headerMotion?.subscribeMomentMillis = { clock.value }
        onDispose { if (playing) headerMotion?.subscribeMomentMillis = null }
    }
    return ToolbarSubscribeMoment(phase, moment, clock) { revealReleased }
}

/**
 * The Subscribe button's container and content colours. A show's moment ends looking exactly like
 * Subscribed, so the button must take those colours in the same frame it reappears; an animation,
 * even a snap, lands a frame late and flashes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun subscribeButtonColors(subscribe: ToolbarSubscribeMoment, style: BrandedToolbarStyle?, accentColor: Color): Pair<Color, Color> {
    val subscribed = subscribe.phase == SubscriptionButtonPhase.SUBSCRIBED
    val container = if (subscribed) {
        style?.subscribedContainer ?: MaterialTheme.colorScheme.surfaceContainerHighest
    } else {
        style?.subscribeContainer ?: accentColor
    }
    val content = if (subscribed) {
        style?.subscribedContent ?: MaterialTheme.colorScheme.onSurface
    } else {
        style?.subscribeContent ?: accentColor.contrastColor()
    }
    val animatedContainer by animateColorAsState(container, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "subscribeContainer")
    val animatedContent by animateColorAsState(content, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "subscribeContent")
    return if (subscribe.moment != null) container to content else animatedContainer to animatedContent
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubscribeButton(
    subscribe: ToolbarSubscribeMoment,
    isSubscribed: Boolean,
    onClick: () -> Unit,
    colors: Pair<Color, Color>,
    compact: Boolean,
    genre: String,
    style: BrandedToolbarStyle?,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
) {
    val subscribeLabel = stringResource(R.string.podcast_info_subscribe)
    val unsubscribeLabel = stringResource(R.string.podcast_info_unsubscribe)
    val labels = BrandedSubscribeLabels(subscribeLabel, stringResource(R.string.podcast_info_subscribed), compact)
    val moment = subscribe.moment
    val playing = subscribe.playing
    // The button morphs to its pressed shape and back on its own clock: Material's built-in morph
    // could be left at the pressed shape after the tap that changed the subscription. It is pressed
    // only while a finger is on it too, so a press whose end never reaches the button can't hold it.
    val pressed by interactionSource.collectIsPressedAsState()
    var fingerDown by remember { mutableStateOf(false) }
    val pressedness = remember { Animatable(0f) }
    val pressSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val pressTarget = subscribePressTarget(pressed, fingerDown)
    LaunchedEffect(pressTarget) { pressedness.animateTo(pressTarget, pressSpec) }
    Box(
        modifier.height(48.dp).graphicsLayer {
            val nudge = if (playing) moment?.nudge(subscribe.clock.value) else null
            translationY = nudge?.offsetY?.toPx() ?: 0f
            scaleX = nudge?.scale ?: 1f
            scaleY = scaleX
        },
    ) {
        Button(
            onClick = onClick,
            shape = subscribeButtonShape(pressedness.value),
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(containerColor = colors.first, contentColor = colors.second),
            contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp),
            // While a show's moment plays it draws the whole button, so the button's own press shape never peeks out.
            modifier = Modifier.fillMaxWidth().height(48.dp).trackFingerDown { fingerDown = it }.graphicsLayer { alpha = if (playing) 0f else 1f }.semantics {
                contentDescription = if (isSubscribed) unsubscribeLabel else subscribeLabel
            },
        ) {
            SubscribeButtonContent(subscribe.phase, hasMoment = moment != null, genre = genre, labels = labels)
        }
        if (playing && moment != null && style != null) {
            moment.Overlay(
                elapsedMillis = { subscribe.clock.value },
                labels = labels,
                style = style,
                shape = SubscribeButtonShape,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun SubscribeButtonContent(phase: SubscriptionButtonPhase, hasMoment: Boolean, genre: String, labels: BrandedSubscribeLabels) {
    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            if (hasMoment && initialState == SubscriptionButtonPhase.CONFIRMATION) {
                // The moment's last frame is the Subscribed label, so it simply stays, unclipped.
                return@AnimatedContent (EnterTransition.None togetherWith ExitTransition.None)
                    .using(SizeTransform(clip = false) { _, _ -> snap() })
            }
            val enter = if (targetState == SubscriptionButtonPhase.CONFIRMATION) {
                fadeIn(tween(120))
            } else {
                fadeIn(tween(160, delayMillis = 60)) + scaleIn(tween(200), initialScale = 0.92f)
            }
            enter
                .togetherWith(fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.96f))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.Center,
        label = "subContent",
    ) { shown ->
        when {
            // The show's own moment draws over the whole button instead.
            shown == SubscriptionButtonPhase.CONFIRMATION && hasMoment -> Unit
            shown == SubscriptionButtonPhase.CONFIRMATION -> SubscriptionGenreConfirmation(genre)
            else -> SubscriptionLabel(
                text = if (shown == SubscriptionButtonPhase.SUBSCRIBED) labels.subscribed else labels.subscribe,
                compact = labels.compact,
            )
        }
    }
}

/** Alerts and auto-download, sliding in beside Subscribed. */
@Suppress("LongParameterList")
@Composable
private fun SubscriptionAutomationToggles(
    reveal: SubscriptionAutomationReveal,
    enabled: Boolean,
    style: BrandedToolbarStyle?,
    notificationsEnabled: Boolean,
    notificationsBlocked: Boolean,
    onNotificationsToggle: () -> Unit,
    autoDownloadEnabled: Boolean,
    onAutoDownloadToggle: () -> Unit,
) {
    SubscriptionToggle(
        checked = notificationsEnabled,
        icon = if (notificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
        label = stringResource(R.string.podcast_info_notifications),
        onClick = onNotificationsToggle,
        blocked = notificationsBlocked,
        enabled = enabled,
        style = style,
        modifier = Modifier.graphicsLayer {
            alpha = reveal.bellAlpha
            scaleX = 0.94f + 0.06f * reveal.bellAlpha
            scaleY = scaleX
        },
    )
    SubscriptionToggle(
        checked = autoDownloadEnabled,
        icon = ImageVector.vectorResource(R.drawable.ic_cloud_download),
        label = stringResource(R.string.podcast_info_auto_download),
        onClick = onAutoDownloadToggle,
        enabled = enabled,
        style = style,
        modifier = Modifier.graphicsLayer {
            alpha = reveal.downloadAlpha
            scaleX = 0.94f + 0.06f * reveal.downloadAlpha
            scaleY = scaleX
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: Boolean = false,
    style: BrandedToolbarStyle? = null,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(
            shape = if (trailing) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
            } else {
                RoundedCornerShape(8.dp)
            },
            pressedShape = RoundedCornerShape(18.dp),
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = style?.actionContainer ?: MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = style?.actionContent ?: MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Icon(icon, null, Modifier.size(21.dp))
    }
}

@Suppress("LongParameterList")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubscriptionToggle(
    checked: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blocked: Boolean = false,
    enabled: Boolean = true,
    style: BrandedToolbarStyle? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val blockedLabel = stringResource(R.string.podcast_info_notifications_blocked)
    val shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(
        shape = RoundedCornerShape(8.dp),
        pressedShape = RoundedCornerShape(18.dp),
    )
    val off = style?.toggleContainer ?: scheme.surfaceContainerHighest
    val offContent = style?.toggleContent ?: scheme.onSurfaceVariant
    val on = if (blocked) scheme.errorContainer else style?.toggleCheckedContainer ?: scheme.primaryContainer
    val onContent = if (blocked) scheme.onErrorContainer else style?.toggleCheckedContent ?: scheme.onPrimaryContainer
    TonalToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shapes = shapes,
        colors = ToggleButtonDefaults.tonalToggleButtonColors(
            containerColor = off,
            contentColor = offContent,
            checkedContainerColor = on,
            checkedContentColor = onContent,
            disabledContainerColor = if (checked) on else off,
            disabledContentColor = if (checked) onContent else offContent,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(48.dp).semantics {
            contentDescription = label
            if (blocked) stateDescription = blockedLabel
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp))
            if (blocked) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(6.dp)
                        .background(scheme.error, CircleShape),
                )
            }
        }
    }
}

/** The Subscribe button's shape: rounded on the leading side, joined to the buttons after it. */
internal val SubscribeButtonShape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 24.dp)

/** The pressed shape shows while the button reports a press and a finger is still on it; otherwise the resting shape. */
internal fun subscribePressTarget(pressed: Boolean, fingerDown: Boolean): Float = if (pressed && fingerDown) 1f else 0f

/**
 * Reports whether a finger is on this element, without consuming anything, so its own click still
 * works. A finger that lifts, or a gesture cut off mid-way, always reports false.
 */
private fun Modifier.trackFingerDown(onChange: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onChange(true)
        try {
            do {
                val event = awaitPointerEvent()
            } while (event.changes.any { it.pressed })
        } finally {
            onChange(false)
        }
    }
}

/** The Subscribe shape [pressed] of the way (0 to 1) to the pressed shape, 18dp on every corner; exactly [SubscribeButtonShape] at rest. */
internal fun subscribeButtonShape(pressed: Float): RoundedCornerShape {
    val p = pressed.coerceIn(0f, 1f)
    if (p == 0f) return SubscribeButtonShape
    val leading = lerp(24.dp, 18.dp, p)
    val trailing = lerp(8.dp, 18.dp, p)
    return RoundedCornerShape(topStart = leading, topEnd = trailing, bottomEnd = trailing, bottomStart = leading)
}

/** The Subscribe or Subscribed label, shared with a show's own subscribe moment so its last frame matches. */
@Composable
internal fun SubscriptionLabel(text: String, compact: Boolean, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text = text,
        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
        fontWeight = GoogleSansWeight.bold,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
