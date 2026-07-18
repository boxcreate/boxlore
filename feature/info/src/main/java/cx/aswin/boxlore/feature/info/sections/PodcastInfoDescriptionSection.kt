package cx.aswin.boxlore.feature.info.sections

import android.net.Uri
import android.text.Html
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.PodrollItem

internal fun stripHtml(html: String?): String {
    if (html.isNullOrEmpty()) return ""
    return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
}


@Composable
internal fun LockedFeedNotice() {
    var showLockedInfoDialog by remember { mutableStateOf(false) }

    Spacer(modifier = Modifier.height(12.dp))
    androidx.compose.material3.HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .expressiveClickable(isolate = true) { showLockedInfoDialog = true }
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Locked",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Podcast feed is locked",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error,
        )
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = "Information",
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
    }

    if (showLockedInfoDialog) {
        AlertDialog(
            onDismissRequest = { showLockedInfoDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    text = "Podcast Locked",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "This podcast's feed has been locked by its publisher. According to the Podcasting 2.0 specification, a locked feed prevents other directory platforms or hosting services from importing or migrating this show's feed without the owner's explicit authorization.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showLockedInfoDialog = false }) {
                    Text("Got it")
                }
            },
        )
    }
}

@Composable
internal fun PodrollRecommendations(
    podroll: List<PodrollItem>,
    onPodcastClick: (String) -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))
    androidx.compose.material3.HorizontalDivider(
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = "Creator Recommends",
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(12.dp))

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(podroll) { item ->
            RecommendedPodcastCard(
                item = item,
                onPodcastClick = onPodcastClick,
            )
        }
    }
}

@Composable
internal fun PodcastInfoDescriptionSection(
    strippedDesc: String,
    isLocked: Boolean,
    podroll: List<PodrollItem>?,
    isDescExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPodcastClick: (String) -> Unit,
) {
    val hasPodroll = !podroll.isNullOrEmpty()
    if (strippedDesc.isEmpty() && !isLocked && !hasPodroll) return

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                ).expressiveClickable { onToggleExpanded() },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        PodcastInfoDescriptionBody(
            strippedDesc = strippedDesc,
            isLocked = isLocked,
            podroll = podroll,
            hasPodroll = hasPodroll,
            isDescExpanded = isDescExpanded,
            onPodcastClick = onPodcastClick,
        )
    }
}

@Composable
internal fun PodcastInfoDescriptionBody(
    strippedDesc: String,
    isLocked: Boolean,
    podroll: List<PodrollItem>?,
    hasPodroll: Boolean,
    isDescExpanded: Boolean,
    onPodcastClick: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
    ) {
        if (strippedDesc.isNotEmpty()) {
            Text(
                text = strippedDesc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (isDescExpanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // With no description there's nothing to "expand" — always show the lock notice and
        // podroll in that case instead of hiding them behind a collapsed, empty description.
        val showMetadataRegardless = isDescExpanded || strippedDesc.isEmpty()
        if (showMetadataRegardless && isLocked) {
            LockedFeedNotice()
        }
        if (showMetadataRegardless && hasPodroll) {
            PodrollRecommendations(podroll = podroll.orEmpty(), onPodcastClick = onPodcastClick)
        }
    }
}


@Composable
internal fun RecommendedPodcastCard(
    item: cx.aswin.boxlore.core.model.PodrollItem,
    onPodcastClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .expressiveClickable(isolate = true) {
                    val targetId =
                        if (!item.uuid.isNullOrBlank()) {
                            "guid:${item.uuid}"
                        } else {
                            val encoded = Uri.encode(item.url)
                            "url:$encoded"
                        }
                    onPodcastClick(targetId)
                },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Podcasts,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
