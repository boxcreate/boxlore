package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls

/** Stable Compose [testTag] ids for onboarding suggestion instrumentation / Maestro. */
internal object SuggestedPodcastTestTags {
    const val TOGGLE = "onboarding_subscribe_toggle"
    const val GRID_CARD = "onboarding_suggestion_grid_card"
}

/** Width of a suggestion card; its artwork is square. */
internal val SuggestionCardWidth = 140.dp
private val SuggestionCardShape = RoundedCornerShape(22.dp)

/** The whole card's press outline: round at the top like the artwork, tight at the bottom so text is never clipped. */
private val SuggestionCardPressShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 8.dp, bottomEnd = 8.dp)

/**
 * A show in a suggestions lane: square artwork, the name on two lines and the host on one. A tap
 * adds or removes it; the badge on the artwork blooms from a plus into a filled, turned shape with
 * a check, the same selection mark as Echo's answers, and the artwork gains a ring. Touch and hold
 * opens the description sheet.
 */
@Composable
internal fun SuggestionShowCard(
    podcast: Podcast,
    selected: Boolean,
    shapeIndex: Int,
    onToggle: (String) -> Unit,
    onOpenDetails: (Podcast) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val ring by animateDpAsState(if (selected) 3.dp else 0.dp, label = "Suggestion ring")
    Column(
        modifier
            .width(SuggestionCardWidth)
            .testTag(SuggestedPodcastTestTags.GRID_CARD)
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                toggleableState = ToggleableState(selected)
            }
            .expressiveClickable(
                shape = SuggestionCardPressShape,
                onLongClickLabel = stringResource(R.string.suggestions_details),
                onLongClick = { onOpenDetails(podcast) },
            ) { onToggle(podcast.id) },
    ) {
        Box(Modifier.size(SuggestionCardWidth)) {
            OptimizedImage(
                url = podcast.imageUrl,
                fallbackUrls = podcast.artworkUrls(),
                proxyWidth = 360,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(SuggestionCardShape),
            )
            if (ring > 0.dp) Box(Modifier.matchParentSize().border(ring, colors.primary, SuggestionCardShape))
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag(SuggestedPodcastTestTags.TOGGLE)) {
                EchoSelectionBadge(selected, if (selected) Icons.Rounded.Check else Icons.Rounded.Add, size = 36.dp, shapeIndex = shapeIndex)
            }
        }
        Text(
            podcast.title,
            Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = GoogleSansWeight.bold),
            color = colors.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            podcast.artist,
            Modifier.padding(bottom = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
                .fillMaxWidth().tabletSheetScroll()
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
            fallbackUrls = podcast.artworkUrls(),
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
