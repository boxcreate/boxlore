package cx.aswin.boxlore.feature.info.sections

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentHeightDp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.isTrailer
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.components.ExpandableInfoTitle
import cx.aswin.boxlore.feature.info.components.MetadataChipsLayout
import cx.aswin.boxlore.feature.info.components.PodcastInfoMetadataChipsRow
import cx.aswin.boxlore.feature.info.components.calculateUpdateFrequencyData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The regular show header. It picks its form from its own size, never the window's: the cover
 * beside the title on a short pane (a sideways phone, a tabletop half) so Subscribe and the first
 * episode show at once, and beside the title, chips and description on a page 600dp or wider; the
 * centred header otherwise, with a smaller cover on pages under 800dp tall. Item 0 of the list in
 * every form, so the Jump pill's item count and the title motion hold.
 */
@Composable
internal fun PodcastInfoHeroSection(
    state: PodcastInfoUiState.Success,
    sortedPersons: List<Person>,
    isDescExpanded: Boolean,
    onDescExpandedChange: (Boolean) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onPodcastClick: (String) -> Unit,
    onEditGenre: () -> Unit = {},
) {
    val pageHeight = boxloreContentHeightDp()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val header = showHeaderLayout(widthDp = maxWidth.value, pageHeightDp = pageHeight)
        val parts = ShowHeaderParts(state, sortedPersons, isDescExpanded, onDescExpandedChange, onPlayEpisode, onPodcastClick, onEditGenre)
        when (header.form) {
            ShowHeaderForm.Stacked -> StackedShowHeader(parts, header.coverSize)
            else -> ShowHeaderHorizontal(parts, header)
        }
    }
}

/** What every header form shows, and its callbacks. */
internal class ShowHeaderParts(
    val state: PodcastInfoUiState.Success,
    val sortedPersons: List<Person>,
    val isDescExpanded: Boolean,
    val onDescExpandedChange: (Boolean) -> Unit,
    val onPlayEpisode: (Episode) -> Unit,
    val onPodcastClick: (String) -> Unit,
    val onEditGenre: () -> Unit,
)

@Composable
private fun StackedShowHeader(parts: ShowHeaderParts, coverSize: Dp) {
    val state = parts.state
    Column(modifier = Modifier.fillMaxWidth().testTag(SHOW_HEADER_STACKED_TAG), horizontalAlignment = Alignment.CenterHorizontally) {
        ShowCover(parts, coverSize)

        Spacer(modifier = Modifier.height(20.dp))

        ExpandableInfoTitle(
            itemId = state.podcast.id,
            title = state.podcast.title,
            collapsedLines = 2,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.podcast.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = GoogleSansWeight.medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Full width, so a long row scrolls out to the page's edge instead of stopping 16dp in.
        PodcastInfoChipsRow(
            state = state,
            sortedPersons = parts.sortedPersons,
            onPlayEpisode = parts.onPlayEpisode,
            onEditGenre = parts.onEditGenre,
        )

        Spacer(modifier = Modifier.height(18.dp))

        ShowDescription(parts, Modifier.padding(horizontal = 16.dp))
    }
}

/**
 * The cover on the left with the title, publisher and chips beside it: [ShowHeaderForm.Row] on a
 * short pane (the description under the row), [ShowHeaderForm.Wide] on a wide page (the chips wrap
 * and the description sits in the column, three lines until opened).
 */
@Composable
internal fun ShowHeaderHorizontal(parts: ShowHeaderParts, header: ShowHeaderLayout) {
    val state = parts.state
    val wide = header.form == ShowHeaderForm.Wide
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(SHOW_HEADER_HORIZONTAL_TAG)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = if (wide) Alignment.Top else Alignment.CenterVertically) {
            ShowCover(parts, header.coverSize)
            Spacer(Modifier.size(if (wide) 24.dp else 16.dp))
            Column(Modifier.weight(1f)) {
                ExpandableInfoTitle(
                    itemId = state.podcast.id,
                    title = state.podcast.title,
                    collapsedLines = header.titleLines,
                    textAlign = TextAlign.Start,
                    baseStyle = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.podcast.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = GoogleSansWeight.medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                PodcastInfoChipsRow(
                    state = state,
                    sortedPersons = parts.sortedPersons,
                    onPlayEpisode = parts.onPlayEpisode,
                    onEditGenre = parts.onEditGenre,
                    layout = if (wide) MetadataChipsLayout.Wrapping else MetadataChipsLayout.ScrollingRow,
                    // Beside the cover the row starts at the title's edge.
                    contentPadding = PaddingValues(),
                )
                if (wide) {
                    Spacer(Modifier.height(16.dp))
                    ShowDescription(parts, collapsedLines = 3)
                }
            }
        }
        if (!wide) {
            Spacer(Modifier.height(16.dp))
            ShowDescription(parts)
        }
    }
}

@Composable
private fun ShowCover(parts: ShowHeaderParts, size: Dp) {
    val podcast = parts.state.podcast
    Surface(
        modifier = Modifier.size(size).testTag(SHOW_HEADER_COVER_TAG),
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 8.dp,
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = listOf(podcast.fallbackImageUrl, podcast.latestEpisode?.imageUrl),
            proxyWidth = if (size > CoverSizeTall) 640 else 600,
            contentDescription = podcast.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun ShowDescription(parts: ShowHeaderParts, modifier: Modifier = Modifier, collapsedLines: Int = 2) {
    val podcast = parts.state.podcast
    val strippedDesc = remember(podcast.description) { stripHtml(podcast.description) }
    PodcastInfoDescriptionSection(
        strippedDesc = strippedDesc,
        isLocked = podcast.isLocked,
        podroll = podcast.podroll,
        isDescExpanded = parts.isDescExpanded,
        onToggleExpanded = { parts.onDescExpandedChange(!parts.isDescExpanded) },
        onPodcastClick = parts.onPodcastClick,
        modifier = modifier,
        collapsedLines = collapsedLines,
    )
}

/** The show page's header forms. */
internal enum class ShowHeaderForm { Stacked, Row, Wide }

/** A header form, its cover size and how many lines its title shows before the full-title control. */
internal data class ShowHeaderLayout(val form: ShowHeaderForm, val coverSize: Dp, val titleLines: Int = 2)

/**
 * The header for a page [widthDp] wide (the header's own width) in a pane [pageHeightDp] tall.
 * Short panes (under 480dp) put the cover beside the title; pages 600dp or wider get the wide
 * two-column header; otherwise the centred header, with a 152dp cover under 800dp tall and the
 * phone's 180dp cover from there.
 */
internal fun showHeaderLayout(widthDp: Float, pageHeightDp: Float): ShowHeaderLayout = when {
    pageHeightDp < SHORT_PANE_DP -> ShowHeaderLayout(ShowHeaderForm.Row, CoverSizeShort, titleLines = 2)
    widthDp >= WIDE_HEADER_DP -> ShowHeaderLayout(ShowHeaderForm.Wide, CoverSizeWide, titleLines = 3)
    pageHeightDp < TALL_PAGE_DP -> ShowHeaderLayout(ShowHeaderForm.Stacked, CoverSizeMedium)
    else -> ShowHeaderLayout(ShowHeaderForm.Stacked, CoverSizeTall)
}

internal val CoverSizeShort = 112.dp
internal val CoverSizeMedium = 152.dp
internal val CoverSizeTall = 180.dp
internal val CoverSizeWide = 208.dp
private const val SHORT_PANE_DP = 480f
private const val WIDE_HEADER_DP = 600f
private const val TALL_PAGE_DP = 800f

internal const val SHOW_HEADER_STACKED_TAG = "podcast_info_header_stacked"
internal const val SHOW_HEADER_HORIZONTAL_TAG = "podcast_info_header_horizontal"
internal const val SHOW_HEADER_COVER_TAG = "podcast_info_header_cover"

/** The show's chips: how often it releases, its genre, people, a trailer and more, in that order. */
@Composable
internal fun PodcastInfoChipsRow(
    state: PodcastInfoUiState.Success,
    sortedPersons: List<Person>,
    onPlayEpisode: (Episode) -> Unit,
    onEditGenre: () -> Unit,
    layout: MetadataChipsLayout = MetadataChipsLayout.ScrollingRow,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
) {
    // Update Frequency Calculation — filters/sorts episodes and analyzes release intervals,
    // which can be expensive for large RSS catalogs, so it runs off the composition thread
    // and is cancelled/restarted automatically as its keys change.
    var cachedFrequencyData by remember(state.podcast.id) { mutableStateOf<Pair<UiText, ImageVector>?>(null) }

    val frequencyData by produceState(
        initialValue = cachedFrequencyData,
        state.podcast,
        state.episodes,
        state.currentSort,
        state.searchQuery,
    ) {
        val computed =
            withContext(Dispatchers.Default) {
                calculateUpdateFrequencyData(
                    podcast = state.podcast,
                    episodes = state.episodes,
                    currentSort = state.currentSort,
                    searchQuery = state.searchQuery,
                    cachedFrequencyData = cachedFrequencyData,
                )
            }
        cachedFrequencyData = computed
        value = computed
    }

    val trailerEpisode =
        remember(state.episodes) {
            state.episodes.firstOrNull { it.isTrailer }
        }

    PodcastInfoMetadataChipsRow(
        podcast = state.podcast,
        sortedPersons = sortedPersons,
        trailerEpisode = trailerEpisode,
        frequencyData = frequencyData,
        onEditGenre = onEditGenre,
        onPlayTrailer = onPlayEpisode,
        layout = layout,
        contentPadding = contentPadding,
        canEditGenre = state.isSubscribed,
    )
}
