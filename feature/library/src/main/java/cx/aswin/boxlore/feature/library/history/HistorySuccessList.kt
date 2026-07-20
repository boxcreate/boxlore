package cx.aswin.boxlore.feature.library.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerHeight
import cx.aswin.boxlore.core.designsystem.component.AppMiniPlayerNavGap
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHeight
import cx.aswin.boxlore.core.model.ListeningHistoryItem
import cx.aswin.boxlore.feature.library.HistorySuccessState
import cx.aswin.boxlore.feature.library.HistoryViewModel
import cx.aswin.boxlore.feature.library.R

@Composable
internal fun HistorySuccessList(
    success: HistorySuccessState,
    viewModel: HistoryViewModel,
    contentPadding: PaddingValues,
    onEpisodeClick: (ListeningHistoryItem) -> Unit,
    onPickOlderDate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomInset =
        AppMiniPlayerHeight + AppMiniPlayerNavGap + AppNavigationBarHeight +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(contentPadding),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = bottomInset + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            HistoryPeriodSelector(
                selected = success.selectedPeriod,
                onSelect = viewModel::setPeriod,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ListeningTimeCard(insights = success.insights)
                HistoryInsightCarousel(insights = success.insights)
            }
        }
        item {
            HistoryActivityGraph(dailyActivity = success.insights.dailyActivity)
        }
        item {
            HistoryTimeOfDayGraph(insights = success.insights)
        }
        item {
            HistoryDayChips(
                activeDays = success.activeDays,
                selectedDate = success.selectedFilterDate,
                onSelectDate = viewModel::setFilterDate,
                onPickOlderDate = onPickOlderDate,
            )
        }
        item {
            HistoryStatusFilterSelector(
                selected = success.selectedHistoryFilter,
                onSelect = viewModel::setHistoryFilter,
            )
        }
        if (success.timelineEmpty) {
            item {
                Text(
                    text = stringResource(R.string.history_timeline_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        } else {
            success.groupedHistory.forEach { (date, itemsForDate) ->
                item(key = "header-$date") {
                    DateHeaderRow(
                        date = date,
                        isExpanded = date in success.expandedDates,
                        onClick = { viewModel.toggleDateExpansion(date) },
                    )
                }
                item(key = "group-$date") {
                    AnimatedVisibility(
                        visible = date in success.expandedDates,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            itemsForDate.forEach { item ->
                                key(item.episodeId) {
                                    HistoryTimelineItem(
                                        item = item,
                                        onClick = {
                                            viewModel.episodesClickedCount++
                                            onEpisodeClick(item)
                                        },
                                        onDelete = {
                                            viewModel.removeHistoryItem(item.episodeId)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
