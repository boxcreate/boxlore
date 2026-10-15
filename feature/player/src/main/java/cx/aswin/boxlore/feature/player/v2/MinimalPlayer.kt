package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.theme.FontRoundness
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.LocalFontRoundness
import cx.aswin.boxlore.core.designsystem.theme.buildBoxLoreTypography
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.feature.player.R
import cx.aswin.boxlore.feature.player.v2.logic.MinimalQueueSwipe
import cx.aswin.boxlore.feature.player.v2.logic.minimalRemainingTime
import java.util.Locale
import kotlin.math.max

/** Original Compose presentation inspired by BunPod; uses boxlore's unchanged player services. */
internal enum class MinimalPlayerSection { Full, Embedded, Heading, Transport }

@Composable
internal fun MinimalPlayerContent(
    state: PlayerState,
    display: FullPlayerDisplay,
    flows: PlayerProgressFlows,
    actions: MinimalPlayerActions,
    modifier: Modifier = Modifier,
    section: MinimalPlayerSection = MinimalPlayerSection.Full,
) {
    val colors = display.colorScheme
    val statusInset = if (section == MinimalPlayerSection.Full) WindowInsets.statusBars.asPaddingValues().calculateTopPadding() else 0.dp
    val navInset = if (section == MinimalPlayerSection.Full) WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() else 0.dp
    val queueGesture = rememberMinimalQueueGesture(display.isExpanded, actions.onQueue)
    val context = LocalContext.current
    val typography = remember(context) { buildBoxLoreTypography(context, FontRoundness.AXIS_CRISP) }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height
    val windowHeight = with(LocalDensity.current) { if (windowHeightPx > 0) windowHeightPx.toDp() else Dp.Infinity }
    CompositionLocalProvider(LocalFontRoundness provides FontRoundness.AXIS_CRISP) {
        MaterialTheme(colorScheme = colors, typography = typography) {
            // The sheet's animation canvas deliberately extends below the window. Only this
            // presentation anchors its controls to the actual visible window, before insets.
            BoxWithConstraints(
                modifier.heightIn(max = windowHeight).fillMaxSize().background(colors.surface)
                    .padding(top = statusInset, bottom = navInset)
                    .nestedScroll(display.sheetNestedScrollConnection).nestedScroll(queueGesture),
            ) {
                MinimalPlayerLayout(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 24.dp),
                    handle = {
                        if (section == MinimalPlayerSection.Full) MinimalPlayerHandle(colors, actions.onCollapse) else Spacer(Modifier.height(0.dp))
                    },
                    heading = {
                        if (section != MinimalPlayerSection.Transport) MinimalPlayerHeading(state, colors, actions) else Spacer(Modifier.height(0.dp))
                    },
                    transport = {
                        if (section != MinimalPlayerSection.Heading) {
                            Column {
                                MinimalPlayerProgress(state, flows, display.isExpanded, colors, actions.onSeek)
                                MinimalPlayerControls(state, colors, actions)
                                Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                                    IconButton(onClick = actions.onQueue, modifier = Modifier.size(48.dp)) {
                                        Icon(Icons.Rounded.ExpandLess, stringResource(R.string.minimal_up_next), Modifier.size(26.dp), tint = colors.onSurfaceVariant)
                                    }
                                }
                            }
                        } else {
                            Spacer(Modifier.height(0.dp))
                        }
                    },
                )
            }
        }
    }
}

/** Center the title in the upper space; let the whole canvas scroll only when it cannot fit. */
@Composable
private fun MinimalPlayerLayout(
    modifier: Modifier,
    handle: @Composable () -> Unit,
    heading: @Composable () -> Unit,
    transport: @Composable () -> Unit,
) {
    Layout(content = {
        handle()
        heading()
        transport()
    }, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minHeight = 0)
        val handlePlaceable = measurables[0].measure(loose)
        val headingPlaceable = measurables[1].measure(loose)
        val transportPlaceable = measurables[2].measure(loose)
        val gap = 16.dp.roundToPx()
        val minimumHeight = handlePlaceable.height + headingPlaceable.height + transportPlaceable.height + gap * 2
        val height = constraints.constrainHeight(max(constraints.minHeight, minimumHeight))
        val upperHeight = height - handlePlaceable.height - transportPlaceable.height
        layout(constraints.maxWidth, height) {
            handlePlaceable.placeRelative(0, 0)
            headingPlaceable.placeRelative(0, handlePlaceable.height + (upperHeight - headingPlaceable.height) / 2)
            transportPlaceable.placeRelative(0, height - transportPlaceable.height)
        }
    }
}

@Composable
private fun MinimalPlayerHandle(colors: ColorScheme, onCollapse: () -> Unit) {
    val description = stringResource(R.string.minimal_collapse)
    Box(
        Modifier.fillMaxWidth().height(48.dp).semantics { contentDescription = description }
            .clickable(role = Role.Button, onClick = onCollapse),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp, 3.dp).background(colors.onSurface.copy(alpha = 0.25f), RoundedCornerShape(50)))
    }
}

@Composable
private fun MinimalPlayerHeading(state: PlayerState, colors: ColorScheme, actions: MinimalPlayerActions) {
    var fullTitle by rememberSaveable(state.currentEpisode?.id) { mutableStateOf(false) }
    val title = state.currentEpisode?.title.orEmpty()
    val show = state.currentPodcast?.title.orEmpty()
    val titleHeight = with(LocalDensity.current) { 160.sp.toDp() }
    Column {
        Text(
            show.uppercase(Locale.getDefault()),
            Modifier.fillMaxWidth().padding(bottom = 8.dp).clickable(role = Role.Button, onClick = actions.onPodcastInfo),
            color = colors.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 1.25.em),
            letterSpacing = 0.8.sp,
            maxLines = 2,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 16.sp),
            overflow = TextOverflow.Ellipsis,
        )
        val fullTitleDescription = stringResource(R.string.minimal_full_title)
        MinimalEpisodeTitle(
            title = title,
            color = colors.primary,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = GoogleSansWeight.medium,
                lineHeight = 1.14.em,
                letterSpacing = (-0.7).sp,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(max = titleHeight).clickable(role = Role.Button, onClickLabel = fullTitleDescription) { fullTitle = true },
        )
    }
    if (fullTitle) {
        AlertDialog(
            onDismissRequest = { fullTitle = false },
            title = { Text(show) },
            text = { Text(title, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { fullTitle = false }) { Text(stringResource(R.string.minimal_done)) } },
        )
    }
}

@Composable
private fun MinimalPlayerProgress(state: PlayerState, flows: PlayerProgressFlows, visible: Boolean, colors: ColorScheme, onSeek: (Long) -> Unit) {
    val position by flows.position.collectAsStateWithLifecycle(initialValue = 0L)
    val time = minimalRemainingTime(state.duration, position)
    val label = when {
        state.duration <= 0 -> stringResource(R.string.minimal_unknown_duration)
        time.hours > 0 -> stringResource(R.string.minimal_remaining_hours, time.hours, time.minutes)
        else -> stringResource(R.string.minimal_remaining_minutes, time.minutes)
    }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            label,
            Modifier.fillMaxWidth(),
            color = colors.primary,
            style = MaterialTheme.typography.displayMedium.copy(fontWeight = GoogleSansWeight.bold, lineHeight = 1.08.em, letterSpacing = (-1.8).sp, textDirection = TextDirection.ContentOrLtr),
            textAlign = TextAlign.End,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 32.sp, maxFontSize = 64.sp),
        )
        MinimalPlayerSeekbar(flows, state.duration, state.isPlaying && !state.isLoading && visible, colors, onSeek, state.currentChapters)
    }
}

@Composable
private fun rememberMinimalQueueGesture(expanded: Boolean, onOpen: () -> Unit): NestedScrollConnection {
    val open by rememberUpdatedState(onOpen)
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    return remember(expanded, threshold) {
        val swipe = MinimalQueueSwipe(threshold)
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (expanded && source == NestedScrollSource.UserInput && swipe.drag(available.y)) open()
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                swipe.reset()
                return Velocity.Zero
            }
        }
    }
}
