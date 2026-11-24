package cx.aswin.boxlore.feature.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogConfirmButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogDismissButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIcon
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIconTone
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafeContentPadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreHorizontalSafePadding
import cx.aswin.boxlore.core.designsystem.layout.boxloreReadingColumn
import cx.aswin.boxlore.core.designsystem.text.currentLocale
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnHistoryScreen(
    viewModel: LearnHistoryViewModel,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onEpisodeClick: (Episode) -> Unit
) {
    TrackScreenView("learn_history")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    // The filter and scroll belong to the screen, so they survive resizing and recreation.
    var filter by rememberSaveable { mutableStateOf(LearnHistoryFilter.All) }
    val listState = rememberLazyListState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (showClearDialog) {
        TrackScreenView("learn_history_clear_confirmation")
        BoxloreAlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.lore_history_clear_title)) },
            text = { Text(stringResource(R.string.lore_history_clear_body)) },
            icon = { BoxloreDialogIcon(Icons.Rounded.ClearAll, tone = BoxloreDialogIconTone.Destructive) },
            confirmButton = {
                BoxloreDialogConfirmButton(
                    text = stringResource(R.string.lore_history_clear_confirm),
                    destructive = true,
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                )
            },
            dismissButton = {
                BoxloreDialogDismissButton(text = stringResource(R.string.lore_history_cancel), onClick = { showClearDialog = false })
            }
        )
    }

    LoreHaloBackground(accentColor = MaterialTheme.colorScheme.primary) {
        Scaffold(
            topBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .boxloreHorizontalSafePadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    LoreRoundIconButton(
                        icon = Icons.AutoMirrored.Rounded.ArrowBack,
                        label = stringResource(R.string.explore_back),
                        onClick = onBack,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(
                            id = cx.aswin.boxlore.core.designsystem.R.drawable.logo_lore
                        ),
                        contentDescription = stringResource(R.string.lore_name),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                            MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier
                            .height(32.dp)
                            .align(Alignment.Center)
                    )
                    if (uiState is LearnHistoryUiState.Success &&
                        (uiState as LearnHistoryUiState.Success).entries.isNotEmpty()
                    ) {
                        LoreRoundIconButton(
                            icon = Icons.Rounded.ClearAll,
                            label = stringResource(R.string.lore_history_clear),
                            onClick = { showClearDialog = true },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            },
            containerColor = androidx.compose.ui.graphics.Color.Transparent
        ) { innerPadding ->
            when (val state = uiState) {
                is LearnHistoryUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(bottom = bottomContentPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader.Expressive(size = 64.dp)
                    }
                }
                is LearnHistoryUiState.Success -> {
                    if (state.entries.isEmpty()) {
                        LearnHistoryEmptyState(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(bottom = bottomContentPadding)
                                .boxloreHorizontalSafePadding()
                        )
                    } else {
                        LearnHistoryList(
                            entries = state.entries,
                            filter = filter,
                            onFilter = { filter = it },
                            listState = listState,
                            contentPadding = boxloreHorizontalSafeContentPadding(
                                PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = bottomContentPadding + 16.dp),
                            ),
                            onEpisodeClick = { entry -> onEpisodeClick(entry.toLearnCuriosityCard().toEpisode()) },
                            onRestore = { entry -> viewModel.restoreEntry(entry.episodeId) },
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                        )
                    }
                }
            }
        }
    }
}

/** The clock Lore history reads the day from; tests move it past midnight. */
internal val LocalLearnHistoryClock = staticCompositionLocalOf<() -> Long> { System::currentTimeMillis }

/** Today in [zone] by [now], moving on at each midnight while the screen stays open. */
@Composable
internal fun rememberToday(zone: ZoneId, now: () -> Long): LocalDate {
    fun read() = Instant.ofEpochMilli(now()).atZone(zone).toLocalDate()
    var today by remember(zone) { mutableStateOf(read()) }
    LaunchedEffect(zone) {
        while (true) {
            val midnight = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            delay((midnight - now()).coerceIn(MIN_DAY_CHECK_MS, MAX_DAY_CHECK_MS))
            today = read()
        }
    }
    return today
}

private const val MIN_DAY_CHECK_MS = 1_000L
private const val MAX_DAY_CHECK_MS = 60 * 60 * 1_000L

/** The title, the filters and the cards grouped by when they were seen, in one readable column. */
@Composable
@Suppress("LongParameterList") // The list's content, its screen-owned state and its two actions.
private fun LearnHistoryList(
    entries: List<LearnHistoryEntry>,
    filter: LearnHistoryFilter,
    onFilter: (LearnHistoryFilter) -> Unit,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onEpisodeClick: (LearnHistoryEntry) -> Unit,
    onRestore: (LearnHistoryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val now = LocalLearnHistoryClock.current
    val zone = ZoneId.systemDefault()
    // Regrouped when the day turns, so a page left open past midnight moves Today's cards to Yesterday.
    val today = rememberToday(zone, now)
    val groups = remember(entries, filter, locale, zone, today) {
        groupLearnHistory(entries.filter(filter::keeps), now(), zone, locale)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "history_title") {
            Column(Modifier.boxloreReadingColumn(LearnHistoryMaxWidthDp).padding(start = 4.dp, end = 4.dp, bottom = 12.dp)) {
                Text(
                    text = stringResource(R.string.lore_history_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = GoogleSansWeight.bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.lore_history_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item(key = "history_filters") {
            LearnHistoryFilterRow(selected = filter, onSelect = onFilter, modifier = Modifier.boxloreReadingColumn(LearnHistoryMaxWidthDp))
        }
        if (groups.isEmpty() && filter != LearnHistoryFilter.All) {
            item(key = "history_filter_empty") {
                LearnHistoryFilterEmpty(filter, onShowAll = { onFilter(LearnHistoryFilter.All) }, modifier = Modifier.boxloreReadingColumn(LearnHistoryMaxWidthDp))
            }
        }
        groups.forEach { group ->
            item(key = "history_group_${group.period.key()}") {
                LearnHistoryGroupHeader(group.period, Modifier.boxloreReadingColumn(LearnHistoryMaxWidthDp))
            }
            itemsIndexed(group.entries, key = { _, entry -> entry.episodeId }) { index, entry ->
                LearnHistoryRow(
                    entry = entry,
                    shape = learnHistoryRowShape(index, group.entries.size),
                    onClick = { onEpisodeClick(entry) },
                    onRestore = { onRestore(entry) },
                    modifier = Modifier.boxloreReadingColumn(LearnHistoryMaxWidthDp).testTag(LEARN_HISTORY_ROW_TAG),
                )
            }
        }
    }
}

private fun LearnHistoryPeriod.key(): String = when (this) {
    is LearnHistoryPeriod.Month -> "$year-$month"
    else -> toString()
}

/** Lore history's list, title and empty card stay this wide at most on every window. */
private const val LearnHistoryMaxWidthDp = 720f

internal const val LEARN_HISTORY_ROW_TAG = "learn_history_row"

@Composable
private fun LearnHistoryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            modifier = Modifier.boxloreReadingColumn(LearnHistoryMaxWidthDp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes.Cookie6,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(16.dp)
                            .size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.lore_history_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = GoogleSansWeight.bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.lore_history_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
