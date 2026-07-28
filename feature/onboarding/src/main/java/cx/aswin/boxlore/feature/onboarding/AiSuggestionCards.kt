package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background

/** Stable Compose [testTag] ids for onboarding suggestion instrumentation / Maestro. */
internal object SuggestedPodcastTestTags {
    const val TOGGLE = "onboarding_subscribe_toggle"
    const val GRID_CARD = "onboarding_suggestion_grid_card"
}

/**
 * Compact 2-col grid card: art-forward select, genre chip, info for description sheet.
 */
@Composable
internal fun SuggestionSelectCard(
    podcast: Podcast,
    isSubscribed: Boolean,
    onToggleSubscription: (String) -> Unit,
    onOpenDetails: (Podcast) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue =
            if (isSubscribed) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            },
        label = "suggestionCardBorder",
    )
    val containerColor by animateColorAsState(
        targetValue =
            if (isSubscribed) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        label = "suggestionCardBg",
    )

    OutlinedCard(
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(if (isSubscribed) 2.dp else 1.dp, borderColor),
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(SuggestedPodcastTestTags.GRID_CARD)
                .expressiveClickable(shape = MaterialTheme.shapes.large) {
                    onToggleSubscription(podcast.id)
                },
        colors =
            androidx.compose.material3.CardDefaults.outlinedCardColors(
                containerColor = containerColor,
            ),
    ) {
        Column {
            SuggestionCardArtwork(
                podcast = podcast,
                isSubscribed = isSubscribed,
                onOpenDetails = onOpenDetails,
            )
            SuggestionCardFooter(podcast = podcast, isSubscribed = isSubscribed)
        }
    }
}

@Composable
private fun SuggestionCardArtwork(
    podcast: Podcast,
    isSubscribed: Boolean,
    onOpenDetails: (Podcast) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            proxyWidth = 400,
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0f to Color.Transparent,
                                    0.55f to Color.Transparent,
                                    1f to Color.Black.copy(alpha = 0.45f),
                                ),
                        ),
                    ),
        )
        SuggestionCardGenreChip(genre = podcast.genre.trim())
        IconButton(
            onClick = { onOpenDetails(podcast) },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(36.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = "About ${podcast.title}",
                    tint = Color.White,
                    modifier =
                        Modifier
                            .padding(6.dp)
                            .size(16.dp),
                )
            }
        }
        SuggestionCardSelectBadge(isSubscribed = isSubscribed)
    }
}

@Composable
private fun BoxScope.SuggestionCardGenreChip(genre: String) {
    if (genre.isEmpty() || genre.equals("Podcast", ignoreCase = true)) return
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
        modifier =
            Modifier
                .padding(8.dp)
                .align(Alignment.TopStart),
    ) {
        Text(
            text = genre,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun BoxScope.SuggestionCardSelectBadge(isSubscribed: Boolean) {
    Surface(
        shape = CircleShape,
        color =
            if (isSubscribed) {
                MaterialTheme.colorScheme.primary
            } else {
                Color.Black.copy(alpha = 0.4f)
            },
        modifier =
            Modifier
                .padding(10.dp)
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .testTag(SuggestedPodcastTestTags.TOGGLE),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isSubscribed) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SuggestionCardFooter(
    podcast: Podcast,
    isSubscribed: Boolean,
) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
        Text(
            text = podcast.title,
            style =
                MaterialTheme.typography.titleSmall.copy(
                    fontWeight = GoogleSansWeight.bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color =
                if (isSubscribed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
        if (podcast.artist.isNotBlank() && podcast.artist != "Unknown") {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = podcast.artist,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SuggestionPodcastDetailSheet(
    podcast: Podcast,
    isSubscribed: Boolean,
    onDismiss: () -> Unit,
    onToggleSubscription: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val description =
        remember(podcast.id, podcast.description, podcast.title, podcast.artist) {
            podcastDescription(podcast)
        }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
        ) {
            SuggestionDetailHeader(podcast = podcast)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .verticalScroll(rememberScrollState()),
            )
            Spacer(modifier = Modifier.height(20.dp))
            SuggestionDetailActions(
                isSubscribed = isSubscribed,
                onDismiss = onDismiss,
                onToggleSubscription = { onToggleSubscription(podcast.id) },
            )
        }
    }
}

@Composable
private fun SuggestionDetailHeader(podcast: Podcast) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            proxyWidth = 240,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(MaterialTheme.shapes.medium),
        )
        Column(modifier = Modifier.weight(1f)) {
            val genre = podcast.genre.trim()
            if (genre.isNotEmpty() && !genre.equals("Podcast", ignoreCase = true)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = genre,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = GoogleSansWeight.semiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = podcast.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = GoogleSansWeight.bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (podcast.artist.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = podcast.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuggestionDetailActions(
    isSubscribed: Boolean,
    onDismiss: () -> Unit,
    onToggleSubscription: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.weight(1f),
        ) {
            Text("Close")
        }
        FilledTonalButton(
            onClick = {
                onToggleSubscription()
                onDismiss()
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(if (isSubscribed) "Remove" else "Add show")
        }
    }
}

internal fun podcastDescription(podcast: Podcast): String {
    val rawDescription = podcast.description?.stripHtml()
    return if (!rawDescription.isNullOrBlank()) {
        rawDescription
    } else {
        "Explore episodes and topics from ${podcast.title}" +
            if (podcast.artist.isNotBlank() && podcast.artist != "Unknown") {
                " by ${podcast.artist}."
            } else {
                "."
            }
    }
}

internal fun String.stripHtml(): String {
    val withoutTags = this.replace(Regex("<[^>]*>"), "")
    return withoutTags
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#8217;", "'")
        .replace("&#8216;", "'")
        .replace("&#8220;", "\"")
        .replace("&#8221;", "\"")
        .replace("&nbsp;", " ")
        .replace("&#39;", "'")
        .replace("&#039;", "'")
        .trim()
}
