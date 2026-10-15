package cx.aswin.boxlore.feature.onboarding

import android.app.Activity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import cx.aswin.boxlore.core.designsystem.R as DesignR
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLogo
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme

@Composable
internal fun WelcomeScreen(
    onHelpMeFind: () -> Unit,
    onSearch: () -> Unit,
    onSkip: () -> Unit,
    onImportClick: () -> Unit,
) {
    val clock = rememberWelcomeMotionClock()
    val scroll = rememberScrollState()
    val colors = MaterialTheme.colorScheme
    // A rich cover panel retains the brand presence in dark mode without a pale billboard.
    val panelColor = if (LocalEffectiveDarkTheme.current) colors.primaryContainer else colors.primary
    val panelContent = if (LocalEffectiveDarkTheme.current) colors.onPrimaryContainer else colors.onPrimary
    var coverPanelHeight by remember { mutableIntStateOf(Int.MAX_VALUE) }
    val statusBarHeight = WindowInsets.statusBars.getTop(LocalDensity.current)
    val coverVisible by remember(scroll, statusBarHeight) {
        derivedStateOf { scroll.value + statusBarHeight < coverPanelHeight }
    }
    WelcomeStatusBars(panelColor, coverVisible)

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.surface)) {
        val viewportHeight = maxHeight
        val viewportWidth = maxWidth
        val coverSize = (viewportWidth * 0.315f).coerceIn(90.dp, 132.dp)
        Column(
            Modifier.fillMaxWidth().verticalScroll(scroll, enabled = scroll.maxValue > 0, overscrollEffect = null).heightIn(min = viewportHeight),
        ) {
            WelcomeCoverPanel(clock, viewportWidth, coverSize, panelColor, panelContent, Modifier.onSizeChanged { coverPanelHeight = it.height })
            Column(Modifier.fillMaxWidth().padding(horizontal = 26.dp).padding(top = 24.dp)) {
                WelcomeInvitation(clock, viewportWidth)
            }
            // Echo's peek borrows the free space above the button; where there is too little, it stays hidden.
            val density = LocalDensity.current
            var peekFits by remember { mutableStateOf(false) }
            Spacer(Modifier.weight(1f).onSizeChanged { peekFits = with(density) { it.height.toDp() } + WelcomeActionsGap >= WelcomeEchoPeekVisible })
            WelcomeActions(
                clock,
                onHelpMeFind,
                onSearch,
                onImportClick,
                onSkip,
                showEcho = peekFits,
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = WelcomeActionsGap).navigationBarsPadding().padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun WelcomeCoverPanel(clock: WelcomeMotionClock, width: Dp, coverSize: Dp, color: Color, contentColor: Color, modifier: Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 46.dp, bottomEnd = 46.dp))
            .background(color).statusBarsPadding().padding(top = 16.dp, bottom = 26.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BoxLoreLogo(textColor = contentColor, height = 24.dp)
            Icon(painterResource(DesignR.drawable.ic_boxlore_app_mark), null, tint = contentColor, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(24.dp))
        WelcomeCoverReel(clock, coverSize, width, color)
    }
}

@Composable
private fun WelcomeInvitation(clock: WelcomeMotionClock, width: Dp) {
    val headline = stringResource(R.string.welcome_curiosity)
    val size = (width.value * 0.11f).coerceIn(36f, 46f).sp
    val headlineStyle = MaterialTheme.typography.displayMedium.copy(
        fontSize = size,
        lineHeight = size * 1.05f,
        fontWeight = GoogleSansWeight.bold,
        letterSpacing = (-0.8).sp,
    )
    // Preserve the whole invitation as one heading for TalkBack; masks only affect its visual entry.
    Column(
        Modifier.clearAndSetSemantics {
        contentDescription = headline
        heading()
    }
    ) {
        WelcomeEntryText(stringResource(R.string.welcome_curiosity_first_line), clock, 100L, headlineStyle)
        WelcomeEntryText(stringResource(R.string.welcome_curiosity_second_line), clock, 230L, headlineStyle)
    }
    Spacer(Modifier.height(10.dp))
    WelcomeRotatingReply(clock)
}

@Composable
private fun WelcomeEntryText(
    text: String,
    clock: WelcomeMotionClock,
    delayMillis: Long,
    style: TextStyle,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Box(Modifier.clip(RoundedCornerShape(0.dp))) {
        Text(
            text,
            style = style,
            color = color,
            modifier = Modifier.graphicsLayer {
                val progress = if (clock.revealComplete.value) {
                    1f
                } else {
                    ((clock.elapsedMillis.value - delayMillis) / 650f).coerceIn(0f, 1f)
                }
                val eased = FastOutSlowInEasing.transform(progress)
                translationY = size.height * (1f - eased)
                alpha = eased
            },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeActions(
    clock: WelcomeMotionClock,
    onHelpMeFind: () -> Unit,
    onSearch: () -> Unit,
    onImportClick: () -> Unit,
    onSkip: () -> Unit,
    showEcho: Boolean,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Echo peeks over the top of the button it leads to; the button hides the rest of it. Its
        // area reaches up into the free space above without adding height (so the page never
        // scrolls for it), and clipping stops its body showing below the button before it rises.
        Box(Modifier.fillMaxWidth()) {
            val findPress = remember { MutableInteractionSource() }
            if (showEcho) {
                Box(Modifier.matchParentSize().reachUp(WelcomeEchoPeekVisible).clipToBounds()) {
                    WelcomeEchoPeek(clock, findPress, Modifier.align(Alignment.TopEnd).padding(end = 8.dp))
                }
            }
            Button(
                onClick = onHelpMeFind,
                interactionSource = findPress,
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                shapes = ButtonDefaults.shapes(shape = RoundedCornerShape(30.dp), pressedShape = RoundedCornerShape(20.dp)),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
            ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, Modifier.padding(end = 10.dp).size(20.dp))
                Text(
                    stringResource(R.string.welcome_find_shows),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.padding(start = 12.dp).size(22.dp))
            }
            }
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            FilledTonalButton(
                onClick = onSearch,
                modifier = Modifier.weight(1.15f).fillMaxHeight().heightIn(min = 52.dp),
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 7.dp, bottomEnd = 7.dp),
                    pressedShape = RoundedCornerShape(18.dp),
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Search, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.welcome_search), style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp), textAlign = TextAlign.Center)
                }
            }
            FilledTonalButton(
                onClick = onImportClick,
                modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 52.dp),
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStart = 7.dp, bottomStart = 7.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                    pressedShape = RoundedCornerShape(18.dp),
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
                    Text(stringResource(R.string.welcome_import), style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp), textAlign = TextAlign.Center)
                }
            }
        }
        TextButton(onClick = onSkip, modifier = Modifier.align(Alignment.CenterHorizontally).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.welcome_skip), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WelcomeStatusBars(panelColor: Color, coverVisible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = WindowCompat.getInsetsController(window, view)
    val defaultLightAppearance by rememberUpdatedState(!LocalEffectiveDarkTheme.current)
    DisposableEffect(view) {
        onDispose { controller.isAppearanceLightStatusBars = defaultLightAppearance }
    }
    SideEffect {
        controller.isAppearanceLightStatusBars = if (coverVisible) panelColor.luminance() > 0.5f else defaultLightAppearance
    }
}

/**
 * Echo's whole animation cell, sized so its head matches the button; the button's top edge sits at
 * Echo's chin (60% down the cell), so only antenna, eyes and smile show.
 */
private val WelcomeEchoCellWidth = 138.dp
private val WelcomeEchoPeekVisible = WelcomeEchoCellWidth / 1.5f * 0.6f

/**
 * Echo, drawn behind the "Find shows for me" button so the button's top edge hides its body. It is
 * Echo's real resting animation (the same one the chat uses), looking straight out and blinking on
 * its own. It rises into view on the shared Welcome clock as the invitation settles, with a small
 * overshoot; with animations off it shows its still pose.
 */
@Composable
private fun WelcomeEchoPeek(clock: WelcomeMotionClock, buttonPress: InteractionSource, modifier: Modifier) {
    // Echo ducks a little while the button is held and pops back up when it is released.
    val pressed by buttonPress.collectIsPressedAsState()
    val duck = animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.55f, stiffness = 650f), label = "Echo duck")
    fun Density.lift(): Float {
        // The clock keeps counting after the text reveal and never replays, so it alone decides
        // the rise; the reveal flag flips at 1.3s, mid-rise.
        val progress = if (clock.animationsDisabled.value) {
            1f
        } else {
            ((clock.elapsedMillis.value - WelcomeEchoRiseDelayMillis) / WelcomeEchoRiseMillis.toFloat()).coerceIn(0f, 1f)
        }
        return (1f - welcomeEchoRise(progress)) * WelcomeEchoPeekVisible.toPx() + duck.value * WelcomeEchoDuck.toPx()
    }
    EchoOnboardingAvatar(
        phase = EchoOnboardingPhase.Rest,
        playbackKey = "welcome",
        prefetch = false,
        modifier = modifier
            .width(WelcomeEchoCellWidth)
            .graphicsLayer {
                translationY = lift()
                // Offscreen, so the contact shadow below darkens only Echo's own pixels.
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                // A faint contact shadow where Echo meets the button's edge, which stays put while
                // Echo moves: it reads as tucked behind the button rather than cut off.
                val edge = WelcomeEchoPeekVisible.toPx() - lift()
                val depth = WelcomeEchoContactShadow.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0f),
                        1f to Color.Black.copy(alpha = 0.16f),
                        startY = edge - depth,
                        endY = edge,
                    ),
                    topLeft = Offset(0f, edge - depth),
                    size = Size(size.width, depth),
                    blendMode = BlendMode.SrcAtop,
                )
            },
    )
}

/** Gap between the invitation's free space and the actions; Echo's peek may use it. */
private val WelcomeActionsGap = 24.dp

/** Draws [extra] above the element's top without taking any layout height. */
private fun Modifier.reachUp(extra: Dp): Modifier = layout { measurable, constraints ->
    val reach = extra.roundToPx()
    val placeable = measurable.measure(constraints.copy(minHeight = constraints.minHeight + reach, maxHeight = constraints.maxHeight + reach))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -reach) }
}

private val WelcomeEchoDuck = 6.dp
private val WelcomeEchoContactShadow = 4.dp

private const val WelcomeEchoRiseDelayMillis = 700L
private const val WelcomeEchoRiseMillis = 420L

/** Ease-out with a small overshoot, so Echo pops up and settles. */
internal fun welcomeEchoRise(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f) - 1f
    return 1f + 2.2f * t * t * t + 1.2f * t * t
}
