package cx.aswin.boxlore.feature.library

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.components.TopBarUtilityActions
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.layout.boxloreContentWidthDp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.LocalEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls

/**
 * Main Library Screen
 */
@Composable
@Suppress("LongParameterList") // One callback per hub card, plus the optional Playlists card.
fun LibraryScreen(
    viewModel: LibraryViewModel,
    entryPoint: String = "bottom_nav",
    onNavigateToLiked: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    onNavigateToDebug: () -> Unit = {},
    onFeedbackClick: () -> Unit = {},
    playlists: LibraryPlaylistsCard? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val homeShortcutsInLibrary by viewModel.homeShortcutsInLibrary.collectAsStateWithLifecycle()
    val lastSeenEpisodes by viewModel.lastSeenEpisodes.collectAsStateWithLifecycle()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.trackHubExit()
            } else if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                viewModel.onScreenResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    TrackScreenView("library") {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHubViewed(entryPoint)
    }

    LibraryContent(
        uiState = uiState,
        onNavigateToLiked = {
            viewModel.hubNavigatedTo = "liked"
            onNavigateToLiked()
        },
        onNavigateToSubscriptions = {
            viewModel.hubNavigatedTo = "subscriptions"
            onNavigateToSubscriptions()
        },
        onNavigateToDownloads = {
            viewModel.hubNavigatedTo = "downloads"
            onNavigateToDownloads()
        },
        onNavigateToHistory = {
            viewModel.hubNavigatedTo = "history"
            onNavigateToHistory()
        },
        showHomeShortcuts = homeShortcutsInLibrary,
        lastSeenEpisodes = lastSeenEpisodes,
        playlists = playlists?.copy(onClick = {
            viewModel.hubNavigatedTo = "playlists"
            playlists.onClick()
        }),
        onFeedbackClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackTopControlbarInteraction("feedback_clicked", "library")
            onFeedbackClick()
        },
        onFeedbackLongClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackTopControlbarInteraction("feedback_long_clicked", "library")
        },
        onSettingsClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackTopControlbarInteraction("settings_clicked", "library")
            onNavigateToSettings()
        },
        onSettingsLongClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackTopControlbarInteraction("avatar_long_clicked", "library")
            onNavigateToDebug()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongParameterList") // One callback per hub card.
fun LibraryContent(
    uiState: LibraryUiState,
    onNavigateToLiked: () -> Unit,
    onNavigateToSubscriptions: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit,
    showHomeShortcuts: Boolean = false,
    onFeedbackClick: () -> Unit = {},
    onFeedbackLongClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onSettingsLongClick: () -> Unit = {},
    playlists: LibraryPlaylistsCard? = null,
    lastSeenEpisodes: Map<String, String> = emptyMap(),
) {
    // One grid state for every window, so folding or unfolding keeps the hub where it was.
    val gridState = rememberLazyGridState()
    val header = rememberLibraryHeaderState()
    val listAtTop = { gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0 }
    val scrollBehavior = rememberLibraryHeaderScrollBehavior(listAtTop)

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            LargeTopAppBar(
                modifier = Modifier.boxloreCardWidth(BoxloreCardFamily.Utility),
                title = {
                    LibraryLargeTitle(
                        title = stringResource(R.string.library_title),
                        collapsedFraction = scrollBehavior.state.collapsedFraction,
                        header = header,
                    )
                },
                actions = {
                    if (showHomeShortcuts) {
                        TopBarUtilityActions(
                            onFeedbackClick = onFeedbackClick,
                            onFeedbackLongClick = onFeedbackLongClick,
                            onSettingsClick = onSettingsClick,
                            onSettingsLongClick = onSettingsLongClick,
                            modifier = Modifier.padding(end = 12.dp),
                            settingsButtonModifier = Modifier.testTag("library_settings_button"),
                        )
                    }
                },
                expandedHeight = header.expandedHeight(),
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = libraryHeaderScrolledColor(listAtTop, if (LocalBoxloreWindowLayout.current.usesTabletLayout) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer),
                ),
            )
        },
    ) { innerPadding ->
        when (uiState) {
            is LibraryUiState.Loading -> LibraryLoading(Modifier.padding(innerPadding)) { LibraryHubSkeleton() }
            is LibraryUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.library_error_loading))
                }
            }
            is LibraryUiState.Success -> LibraryHubGrid(
                state = uiState,
                gridState = gridState,
                destinations = LibraryHubDestinations(onNavigateToSubscriptions, onNavigateToLiked, onNavigateToDownloads, onNavigateToHistory),
                modifier = Modifier.padding(innerPadding),
                playlists = playlists,
                lastSeenEpisodes = lastSeenEpisodes,
            )
        }
    }
}

/**
 * A hub card: its title and a short [status] line bottom-start, the section's artwork as a small
 * collage at the end, and its icon in the top corner. An empty section ([images] empty) shows a
 * medallion in the card's first shape instead of the collage and corner icon. The press follows the
 * card's rounded shape.
 */
@Composable
@Suppress("LongParameterList") // The card's look and its one action.
fun LibraryMenuCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    containerColor: Color,
    images: List<String> = emptyList(),
    shapes: List<Shape> = emptyList(),
    status: String? = null,
    contentColor: Color = contentColorFor(containerColor),
) {
    val shape = RoundedCornerShape(HubCardCorner)
    val layout = LocalBoxloreWindowLayout.current
    val form = when {
        layout.usesTabletLayout -> HubCardForm.Tablet
        // On a sideways phone or a wide split half the collage sits at the card's end, not mid-card.
        boxloreContentWidthDp() > PHONE_MAX_WIDTH_DP -> HubCardForm.WideCompact
        else -> HubCardForm.Phone
    }
    val artwork = images.filter(String::isNotEmpty)
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .boxloreCardWidth(BoxloreCardFamily.Utility)
            .height(HubCardHeight)
            .expressiveClickable(shape = shape, onClick = onClick),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val geometry = hubCardGeometry(maxWidth, form, hasArtwork = artwork.isNotEmpty())
            if (artwork.isEmpty()) {
                EmptyMedallion(icon, shapes.firstOrNull() ?: ExpressiveShapes.Circle, Modifier.align(Alignment.CenterEnd).padding(end = MedallionEndPadding))
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = geometry.collageCenter - CollageBoxWidth / 2)
                        .fillMaxHeight()
                        .width(CollageBoxWidth)
                        .testTag(HUB_CARD_COLLAGE_TAG),
                ) {
                    LibraryCardCollage(images = artwork, shapes = shapes)
                }

                // Scrim to ensure text readability if images slide under text
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    containerColor,
                                    containerColor.copy(alpha = 0.8f),
                                    containerColor.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(CornerIconPadding)
                        .size(CornerIconSize)
                )
            }

            // The title and status stop before the artwork: a long or translated title wraps to its second line instead.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(TitleStartPadding)
                    .widthIn(max = geometry.titleMaxWidth),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = GoogleSansWeight.bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(HUB_CARD_TITLE_TAG),
                )
                if (status != null) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag(HUB_CARD_STATUS_TAG),
                    )
                }
            }
        }
    }
}

/** How a hub card places its collage: today's phone card, a wide compact window, or a tablet card. */
internal enum class HubCardForm { Phone, WideCompact, Tablet }

/** Where a hub card's collage is centred, and how wide its title and status may run before reaching the art. */
internal data class HubCardGeometry(val collageCenter: Dp, val titleMaxWidth: Dp)

/**
 * A hub card's layout for a card [cardWidth] wide. Phones keep today's collage, centred in the
 * card's end half. Wide compact windows put it at the card's end; tablet cards keep it in the end
 * half, clear of the corner icon on cards wide enough to leave the title [TabletTitleRoom]. The title column ends where the art starts (or the empty
 * medallion, when there is no artwork).
 */
internal fun hubCardGeometry(cardWidth: Dp, form: HubCardForm, hasArtwork: Boolean = true): HubCardGeometry {
    val endHalfCenter = (cardWidth - PhoneCollageEndPadding) * END_HALF_CENTER
    val clearOfIcon = cardWidth - CollageIconClearance
    val center = when (form) {
        HubCardForm.Phone -> endHalfCenter
        HubCardForm.WideCompact -> clearOfIcon
        // Clear of the corner icon, unless that would leave the title too little room on a narrow card.
        HubCardForm.Tablet -> minOf(endHalfCenter, maxOf(clearOfIcon, TabletTitleRoom + TitleStartPadding + CollageArtStartReach))
    }
    val artStart = if (hasArtwork) center - CollageArtStartReach else cardWidth - MedallionEndPadding - MedallionSize
    return HubCardGeometry(center, (artStart - TitleStartPadding).coerceAtLeast(0.dp))
}

@Composable
private fun LibraryCardCollage(
    images: List<String>,
    shapes: List<androidx.compose.ui.graphics.Shape>
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        // Fallback shapes if list is empty or short
        val fallbackShapes = listOf(
            ExpressiveShapes.SoftBurst,
            ExpressiveShapes.Circle,
            ExpressiveShapes.Diamond
        )
        val finalShapes = if (shapes.isNotEmpty()) shapes else fallbackShapes

        // Filter out empty URLs
        val validImages = images.filter { it.isNotEmpty() }.take(3)
        val N = validImages.size

        // Reverse order so first image is on top
        validImages.reversed().forEachIndexed { index, imageUrl ->
            // Calculate index within the taken sublist (0=Top, N-1=Bottom)
            val stackIndex = N - 1 - index
            val shape = finalShapes.getOrElse(stackIndex) { finalShapes.first() }

            // Dynamic offsets for "pile" effect
            val xOffset = (stackIndex * 20).dp
            val yOffset = if (stackIndex % 2 == 0) 10.dp else (-10).dp
            val scale = 1f - (stackIndex * 0.15f)
            val rotation = if (stackIndex % 2 == 0) 10f else -10f

            OptimizedImage(
                url = imageUrl,
                proxyWidth = 400,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(80.dp)
                    .offset(x = xOffset, y = yOffset)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = rotation
                    }
                    .clip(shape)
                    .border(2.dp, MaterialTheme.colorScheme.surface, shape)
            )
        }
    }
}

/** An empty section's mark: the section icon on a solid medallion in the card's own shape. */
@Composable
private fun EmptyMedallion(icon: ImageVector, shape: Shape, modifier: Modifier) {
    val fill = if (LocalEffectiveDarkTheme.current) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surface
    Box(
        modifier = modifier
            .size(MedallionSize)
            .clip(shape)
            .background(fill)
            .testTag(HUB_CARD_MEDALLION_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(28.dp))
    }
}

private val HubCardHeight = 120.dp
private val HubCardCorner = 24.dp
private val MedallionSize = 64.dp
private val MedallionEndPadding = 24.dp
private val TitleStartPadding = 16.dp
private val CornerIconPadding = 16.dp
private val CornerIconSize = 32.dp

/** The collage's box: wide enough for its three covers fanned out from the centre. */
private val CollageBoxWidth = 160.dp

/** Today's phone collage: the end half of the card, 16dp in from its edge, art centred in it. */
private val PhoneCollageEndPadding = 16.dp
private const val END_HALF_CENTER = 0.75f

/** How far the collage's art reaches left of its centre: the top cover (80dp) tilted 10 degrees. */
internal val CollageArtStartReach = 47.dp

/** A collage centred this far from the card's end keeps its rightmost cover clear of the corner icon. */
private val CollageIconClearance = 124.dp

/** The least room a tablet card's title gets before the art: "Subscriptions" and "Liked episodes" on one line. */
private val TabletTitleRoom = 150.dp
private const val PHONE_MAX_WIDTH_DP = 430f

/** Test tags for a hub card's title, status line, collage and an empty section's medallion. */
internal const val HUB_CARD_TITLE_TAG = "library_hub_card_title"
internal const val HUB_CARD_STATUS_TAG = "library_hub_card_status"
internal const val HUB_CARD_COLLAGE_TAG = "library_hub_card_collage"
internal const val HUB_CARD_MEDALLION_TAG = "library_hub_card_medallion"

/**
 * Unified Card Style matching ExploreScreen.kt
 * Uses OutlinedCard, specific rounded corners, variable heights.
 */
@Composable
fun LibraryPodcastCard(
    podcast: Podcast,
    isTall: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        shape = MaterialTheme.shapes.large, // Matches ExploreScreen
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.boxloreCardWidth(BoxloreCardFamily.Poster)
            .fillMaxWidth()
            .expressiveClickable(onClick = onClick)
    ) {
        Column {
            // Image Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTall) 200.dp else 150.dp) // Staggered heights (Reduced)
            ) {
                OptimizedImage(
                    url = podcast.imageUrl,
                    fallbackUrls = podcast.artworkUrls(),
                    proxyWidth = 400,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)) // Matches Explore styling
                )

                // Video Badge overlay on image
                if (podcast.medium == "video" || podcast.latestEpisode?.enclosureType?.startsWith("video/") == true) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Box(
                            modifier = Modifier.padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Videocam,
                                contentDescription = stringResource(R.string.library_video_badge),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Text Content with Padding
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = podcast.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
