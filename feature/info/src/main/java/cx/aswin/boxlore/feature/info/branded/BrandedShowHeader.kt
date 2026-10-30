package cx.aswin.boxlore.feature.info.branded

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.sections.LockedFeedNotice
import cx.aswin.boxlore.feature.info.sections.PodcastInfoChipsRow
import cx.aswin.boxlore.feature.info.sections.PodrollRecommendations
import cx.aswin.boxlore.feature.info.sections.stripHtml

/** The episode list's gap between items, plus the action row's own top padding. */
private val ListGapAboveButtons = 14.dp + 8.dp

/** A branded page's header: the show's identity block, then the usual chip row. */
@Composable
@Suppress("LongParameterList")
internal fun BrandedShowHeader(
    page: BrandedShowPage,
    state: PodcastInfoUiState.Success,
    sortedPersons: List<Person>,
    isDescExpanded: Boolean,
    onDescExpandedChange: (Boolean) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onPodcastClick: (String) -> Unit,
    onEditGenre: () -> Unit,
) {
    val chipHeight = LocalBrandedPage.current?.style?.chips?.height ?: BrandedChipRowHeight
    val text = remember(state.podcast.description) { stripHtml(state.podcast.description) }
    val description = BrandedDescription(
        text = text,
        isLocked = state.podcast.isLocked,
        podroll = state.podcast.podroll.orEmpty(),
        expanded = isDescExpanded,
        onToggle = { onDescExpandedChange(!isDescExpanded) },
        onPodcastClick = onPodcastClick,
    )
    page.Header(
        podcast = state.podcast,
        description = description,
        chips = {
            // Chips keep 48dp touch targets; the row takes only the drawn chips' height in the layout.
            Box(Modifier.trimVertical((BrandedChipRowHeight - chipHeight) / 2)) {
                PodcastInfoChipsRow(
                    state = state,
                    sortedPersons = sortedPersons,
                    onPlayEpisode = onPlayEpisode,
                    onEditGenre = onEditGenre,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().pullNextItemUp(ListGapAboveButtons - page.headerToButtons(description)),
    )
}

/**
 * What an opened description shows below its text, as on the regular show page: the locked-feed
 * notice and the shows the publisher recommends. Both also show when there is no text to open.
 */
@Composable
internal fun BrandedDescriptionExtras(description: BrandedDescription) {
    val showExtras = description.expanded || description.text.isEmpty()
    if (showExtras && description.isLocked) LockedFeedNotice()
    if (showExtras && description.podroll.isNotEmpty()) {
        PodrollRecommendations(podroll = description.podroll, onPodcastClick = description.onPodcastClick)
    }
}

/** A branded chip row's height: the minimum touch target, with the chips centred in it. */
internal val BrandedChipRowHeight = 48.dp

/** Reports [amount] less height, so the next list item starts that much closer; nothing is clipped. */
private fun Modifier.pullNextItemUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height - amount.roundToPx()).coerceAtLeast(0)
    layout(placeable.width, height) { placeable.place(0, 0) }
}

/** Takes [amount] off the top and bottom in the layout; the content still draws and takes taps there. */
private fun Modifier.trimVertical(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0))
    val trim = amount.roundToPx()
    layout(placeable.width, (placeable.height - 2 * trim).coerceAtLeast(0)) { placeable.place(0, -trim) }
}
