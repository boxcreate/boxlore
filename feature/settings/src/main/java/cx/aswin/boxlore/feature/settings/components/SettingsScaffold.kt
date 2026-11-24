package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.TwoRowsTopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentBottomPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentHeightDp
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.LocalSettingsPaneBackVisible
import cx.aswin.boxlore.feature.settings.LocalSettingsSplitPane
import cx.aswin.boxlore.feature.settings.R

private val SETTINGS_CONTENT_BOTTOM_PADDING = 220.dp

/** Every Settings page's content column is at most this wide. */
internal val SETTINGS_PAGE_MAX_WIDTH = 720.dp

/** Beside the tablet category list a page starts this far from the list, not centred in its pane. */
internal val SETTINGS_SPLIT_PAGE_START = 24.dp

/** Windows (or panes) shorter than this open a page with its header already slim, so content shows first. */
internal const val SETTINGS_SHORT_WINDOW_DP = 700f

/** The content column: centred and capped on its own, start-aligned beside the tablet category list. */
@Composable
private fun Modifier.settingsPageColumn(): Modifier =
    if (LocalSettingsSplitPane.current) {
        widthIn(max = SETTINGS_PAGE_MAX_WIDTH).fillMaxWidth()
    } else {
        boxloreCardWidth(BoxloreCardFamily.Utility).widthIn(max = SETTINGS_PAGE_MAX_WIDTH).fillMaxWidth()
    }

@Composable
internal fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    actions: @Composable RowScope.() -> Unit = {},
    onUnconsumedTap: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SettingsScaffoldFrame(title, onBack, modifier, actions, onUnconsumedTap) {
        Column(
            modifier = Modifier.settingsPageColumn().verticalScroll(scrollState)
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = boxloreContentBottomPadding(SETTINGS_CONTENT_BOTTOM_PADDING)),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@Composable
internal fun SettingsLazyScaffold(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    SettingsScaffoldFrame(title, onBack, Modifier, {}, null) {
        LazyColumn(
            modifier = Modifier.settingsPageColumn(),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = boxloreContentBottomPadding(SETTINGS_CONTENT_BOTTOM_PADDING)),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScaffoldFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier,
    actions: @Composable RowScope.() -> Unit,
    onUnconsumedTap: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val split = LocalSettingsSplitPane.current
    val short = boxloreContentHeightDp() < SETTINGS_SHORT_WINDOW_DP
    val collapseLimit = with(LocalDensity.current) { -(TopAppBarDefaults.LargeAppBarExpandedHeight - TopAppBarDefaults.LargeAppBarCollapsedHeight).toPx() }
    // Short windows open with the header slim; the one saved state follows later changes of height.
    val barState = rememberTopAppBarState(initialHeightOffsetLimit = collapseLimit, initialHeightOffset = if (short) collapseLimit else 0f)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
    SettingsHeaderFollowsHeight(barState, short, collapseLimit)
    val currentOnUnconsumedTap = rememberUpdatedState(onUnconsumedTap)
    val focusManager = LocalFocusManager.current

    val titlePresentation = settingsHeaderTitlePresentation(
        typography = MaterialTheme.typography,
        collapsedFraction = scrollBehavior.state.collapsedFraction,
        fontScale = LocalDensity.current.fontScale,
        paneWidthDp = boxloreContentWidthDp(),
    )
    val compactTitlePresentation = settingsHeaderTitlePresentation(
        typography = MaterialTheme.typography,
        collapsedFraction = 0f,
        expanded = false,
    )

    Scaffold(
        modifier =
        modifier
            .fillMaxSize()
            .then(if (split) Modifier else Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (split) {
                // Starts where the page starts, with a heavier title than the category list's "Settings".
                TopAppBar(
                    modifier = Modifier.padding(start = SETTINGS_SPLIT_PAGE_START).widthIn(max = SETTINGS_PAGE_MAX_WIDTH),
                    title = {
                        Text(
                            title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = GoogleSansWeight.semiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = { if (LocalSettingsPaneBackVisible.current) SettingsBackButton(onBack) },
                    actions = actions,
                )
            } else {
                SettingsPageTopBar(
                    title = title,
                    onBack = onBack,
                    actions = actions,
                    scrollBehavior = scrollBehavior,
                    short = short,
                    titlePresentation = titlePresentation,
                    compactTitlePresentation = compactTitlePresentation,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // The scaffold's padding already clears side system buttons and a camera cutout.
                .then(if (split) Modifier.padding(start = SETTINGS_SPLIT_PAGE_START) else Modifier)
                .imePadding()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = true)
                        if (waitForUpOrCancellation() != null) {
                            currentOnUnconsumedTap.value?.invoke() ?: focusManager.clearFocus()
                        }
                    }
                },
            contentAlignment = if (split) Alignment.TopStart else Alignment.TopCenter,
        ) {
            content()
        }
    }
}

/**
 * Keeps the header's height in step with the window: it goes slim when the window turns short (a
 * phone turned sideways, a fold, a split screen) and opens again when it is tall enough and the page
 * is at its top. It acts only when the window crosses the line, never against the listener's scroll.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHeaderFollowsHeight(state: TopAppBarState, short: Boolean, collapseLimit: Float) {
    // Saved with the bar state, so a page restored at a different height (rotated while another
    // screen was on top) still opens slim or full as the new window needs.
    val wasShort = rememberSaveable { mutableStateOf(short) }
    LaunchedEffect(short) {
        if (short == wasShort.value) return@LaunchedEffect
        wasShort.value = short
        state.heightOffsetLimit = collapseLimit
        if (short) {
            state.heightOffset = collapseLimit
        } else if (state.contentOffset >= 0f) {
            state.heightOffset = 0f
        }
    }
}

/**
 * The single-pane header. The bar's colour runs the full width of the window; its title, Back and
 * actions sit on the same capped, centred column as the page, inside the same safe area. On short
 * windows, where the header rests slim, it keeps the page colour until the page scrolls under it,
 * and the slim title stays on one line (stepping its size down, then shortening) so no word breaks.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsPageTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    short: Boolean,
    titlePresentation: SettingsHeaderTitlePresentation,
    compactTitlePresentation: SettingsHeaderTitlePresentation,
) {
    val state = scrollBehavior.state
    val pageAtTop by remember(state) { derivedStateOf { state.contentOffset >= 0f } }
    val tablet = LocalBoxloreWindowLayout.current.usesTabletLayout
    val safe = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal)
    val direction = LocalLayoutDirection.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val padding = safe.asPaddingValues()
        val available = maxWidth - padding.calculateStartPadding(direction) - padding.calculateEndPadding(direction)
        val gutter = ((available - SETTINGS_PAGE_MAX_WIDTH) / 2).coerceAtLeast(0.dp)
        TwoRowsTopAppBar(
            title = { expanded ->
                val presentation = if (expanded) titlePresentation else compactTitlePresentation
                val oneLine = short && !expanded
                Text(
                    text = title,
                    modifier = if (expanded) Modifier.padding(bottom = 16.dp) else Modifier,
                    style = presentation.style,
                    maxLines = if (oneLine) 1 else presentation.maxLines,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = if (oneLine) {
                        TextAutoSize.StepBased(minFontSize = SETTINGS_SLIM_TITLE_MIN_SIZE, maxFontSize = maxOf(presentation.style.fontSize.value, SETTINGS_SLIM_TITLE_MIN_SIZE.value).sp)
                    } else {
                        null
                    },
                )
            },
            collapsedHeight = TopAppBarDefaults.LargeAppBarCollapsedHeight,
            expandedHeight = TopAppBarDefaults.LargeAppBarExpandedHeight,
            navigationIcon = { SettingsBackButton(onBack) },
            actions = actions,
            windowInsets = TopAppBarDefaults.windowInsets.add(WindowInsets(left = gutter, right = gutter)),
            scrollBehavior = scrollBehavior,
            colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = if (tablet || (short && pageAtTop)) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
    }
}

/** The slim title on short windows never shrinks below this before it shortens with "…". */
private val SETTINGS_SLIM_TITLE_MIN_SIZE = 16.sp

@Composable
private fun SettingsBackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.settings_back)) }
}
