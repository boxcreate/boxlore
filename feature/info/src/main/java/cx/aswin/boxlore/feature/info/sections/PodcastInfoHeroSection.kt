package cx.aswin.boxlore.feature.info.sections

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
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
import cx.aswin.boxlore.feature.info.components.PodcastInfoMetadataChipsRow
import cx.aswin.boxlore.feature.info.components.calculateUpdateFrequencyData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The regular show header. It picks its form from its own size, never the window's: the cover
 * beside the title on a short pane (a sideways phone, a tabletop half) or a page 600dp or wider but
 * under 600dp tall, so Subscribe and the first episode show at once; beside the title, chips and
 * description on a page 600dp or wider and tall; the centred header otherwise, with a smaller cover
 * on pages under 800dp tall. Item 0 of the list in every form, so the Jump pill's item count and the
 * title motion hold. [onFormChange] hears the form in use, for the title motion.
 */
@Suppress("LongParameterList")
@Composable
internal fun PodcastInfoHeroSection(
    state: PodcastInfoUiState.Success,
    sortedPersons: List<Person>,
    isDescExpanded: Boolean,
    onDescExpandedChange: (Boolean) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onPodcastClick: (String) -> Unit,
    onEditGenre: () -> Unit = {},
    onFormChange: (ShowHeaderForm) -> Unit = {},
) {
    val pageHeight = boxloreContentHeightDp()
    // Held above the form switch, so a rotation, resize or fold that changes the form keeps the
    // full title open, the locked-feed explanation open, the chip row's scroll and the release chip.
    val shared = ShowHeaderShared(
        titleExpanded = rememberSaveable(state.podcast.id, state.podcast.title) { mutableStateOf(false) },
        lockedDialogOpen = rememberSaveable(state.podcast.id) { mutableStateOf(false) },
        chipsState = rememberLazyListState(),
        frequencyData = rememberShowFrequency(state),
    )
    val formChange by rememberUpdatedState(onFormChange)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val header = showHeaderLayout(widthDp = maxWidth.value, pageHeightDp = pageHeight)
        LaunchedEffect(header.form) { formChange(header.form) }
        val parts = ShowHeaderParts(state, sortedPersons, isDescExpanded, onDescExpandedChange, onPlayEpisode, onPodcastClick, onEditGenre, shared)
        when (header.form) {
            ShowHeaderForm.Stacked -> StackedShowHeader(parts, header.coverSize)
            else -> ShowHeaderHorizontal(parts, header)
        }
    }
}

/** What every header form keeps while the form changes. */
internal class ShowHeaderShared(
    val titleExpanded: MutableState<Boolean>,
    val lockedDialogOpen: MutableState<Boolean>,
    val chipsState: LazyListState,
    val frequencyData: Pair<UiText, ImageVector>?,
)

/** What every header form shows, and its callbacks. */
@Suppress("LongParameterList")
internal class ShowHeaderParts(
    val state: PodcastInfoUiState.Success,
    val sortedPersons: List<Person>,
    val isDescExpanded: Boolean,
    val onDescExpandedChange: (Boolean) -> Unit,
    val onPlayEpisode: (Episode) -> Unit,
    val onPodcastClick: (String) -> Unit,
    val onEditGenre: () -> Unit,
    val shared: ShowHeaderShared,
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
            expandedState = parts.shared.titleExpanded,
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
            frequencyData = parts.shared.frequencyData,
            listState = parts.shared.chipsState,
        )

        Spacer(modifier = Modifier.height(18.dp))

        ShowDescription(parts, Modifier.padding(horizontal = 16.dp))
    }
}

/**
 * The cover on the left with the title, publisher and chips beside it: [ShowHeaderForm.Row] with a
 * small cover, [ShowHeaderForm.Wide] with a large one. The chips start at the title's edge and
 * scroll out to the header's end edge. The description sits in the column when it has room
 * ([ShowHeaderLayout.descriptionBeside]), else full width under the row. Over the strongest part
 * of the cover backdrop, the publisher uses the title's colour and information chips get a solid
 * container, so both stay readable on any cover.
 */
@Composable
internal fun ShowHeaderHorizontal(parts: ShowHeaderParts, header: ShowHeaderLayout) {
    val state = parts.state
    val wide = header.form == ShowHeaderForm.Wide
    val endGap = Modifier.padding(end = 16.dp)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp).testTag(SHOW_HEADER_HORIZONTAL_TAG)) {
        Row(verticalAlignment = if (wide) Alignment.Top else Alignment.CenterVertically) {
            ShowCover(parts, header.coverSize)
            Spacer(Modifier.size(if (wide) 24.dp else 16.dp))
            Column(Modifier.weight(1f)) {
                ExpandableInfoTitle(
                    itemId = state.podcast.id,
                    title = state.podcast.title,
                    collapsedLines = header.titleLines,
                    textAlign = TextAlign.Start,
                    baseStyle = if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    modifier = endGap,
                    expandedState = parts.shared.titleExpanded,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.podcast.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = GoogleSansWeight.medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = endGap,
                )
                Spacer(Modifier.height(12.dp))
                PodcastInfoChipsRow(
                    state = state,
                    sortedPersons = parts.sortedPersons,
                    onPlayEpisode = parts.onPlayEpisode,
                    onEditGenre = parts.onEditGenre,
                    frequencyData = parts.shared.frequencyData,
                    listState = parts.shared.chipsState,
                    // Start at the title's edge; scroll out to the header's end, resting 16dp in.
                    contentPadding = PaddingValues(end = 16.dp),
                    alignStart = true,
                    solidInfoChips = true,
                )
                if (header.descriptionBeside) {
                    Spacer(Modifier.height(16.dp))
                    ShowDescription(parts, endGap, collapsedLines = 3)
                }
            }
        }
        if (!header.descriptionBeside) {
            Spacer(Modifier.height(16.dp))
            ShowDescription(parts, endGap)
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
        lockedDialogOpen = parts.shared.lockedDialogOpen,
    )
}

/** The show page's header forms. */
internal enum class ShowHeaderForm { Stacked, Row, Wide }

/**
 * A header form, its cover size, how many lines its title shows before the full-title control, and
 * whether the description sits in the column beside the cover ([descriptionBeside]).
 */
internal data class ShowHeaderLayout(
    val form: ShowHeaderForm,
    val coverSize: Dp,
    val titleLines: Int = 2,
    val descriptionBeside: Boolean = false,
)

/**
 * The header for a page [widthDp] wide (the header's own width) in a pane [pageHeightDp] tall.
 * Short panes (under 480dp), and pages 600dp or wider but under 600dp tall, put a small cover
 * beside the title so Subscribe and the first episode stay on the first screen. Pages 600dp or
 * wider and 600dp or taller get the large two-column header, with the description beside the cover
 * once that column is wide enough to read. Otherwise the centred header: a 152dp cover under 800dp
 * tall, the phone's 180dp cover from there.
 */
internal fun showHeaderLayout(widthDp: Float, pageHeightDp: Float): ShowHeaderLayout = when {
    pageHeightDp < SHORT_PANE_DP -> ShowHeaderLayout(ShowHeaderForm.Row, CoverSizeShort, titleLines = 2)
    widthDp >= WIDE_HEADER_DP && pageHeightDp < WIDE_HEADER_MIN_HEIGHT_DP -> ShowHeaderLayout(ShowHeaderForm.Row, CoverSizeShort, titleLines = 2)
    widthDp >= WIDE_HEADER_DP ->
        ShowHeaderLayout(ShowHeaderForm.Wide, CoverSizeWide, titleLines = 3, descriptionBeside = widthDp >= WIDE_DESCRIPTION_BESIDE_DP)
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

/** The large two-column header needs this much height, or its first screen is all header. */
private const val WIDE_HEADER_MIN_HEIGHT_DP = 600f

/** A header this wide leaves the column beside the 208dp cover about 420dp: room for the description. */
private const val WIDE_DESCRIPTION_BESIDE_DP = 684f

internal const val SHOW_HEADER_STACKED_TAG = "podcast_info_header_stacked"
internal const val SHOW_HEADER_HORIZONTAL_TAG = "podcast_info_header_horizontal"
internal const val SHOW_HEADER_COVER_TAG = "podcast_info_header_cover"

/**
 * How often the show releases, for its chip: worked out off the main thread (filtering and sorting
 * a large catalog can be slow), restarted as the show, its episodes, the sort or the search change,
 * and keeping the last answer meanwhile.
 */
@Composable
internal fun rememberShowFrequency(state: PodcastInfoUiState.Success): Pair<UiText, ImageVector>? {
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
    return frequencyData
}

/**
 * The show's chips: how often it releases, its genre, people, a trailer and more, in that order.
 * Centred while they fit unless [alignStart]; [solidInfoChips] gives information chips a solid
 * container where they sit over the cover backdrop.
 */
@Suppress("LongParameterList")
@Composable
internal fun PodcastInfoChipsRow(
    state: PodcastInfoUiState.Success,
    sortedPersons: List<Person>,
    onPlayEpisode: (Episode) -> Unit,
    onEditGenre: () -> Unit,
    frequencyData: Pair<UiText, ImageVector>? = rememberShowFrequency(state),
    listState: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp),
    alignStart: Boolean = false,
    solidInfoChips: Boolean = false,
) {
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
        contentPadding = contentPadding,
        canEditGenre = state.isSubscribed,
        listState = listState,
        alignStart = alignStart,
        solidInfoChips = solidInfoChips,
    )
}
