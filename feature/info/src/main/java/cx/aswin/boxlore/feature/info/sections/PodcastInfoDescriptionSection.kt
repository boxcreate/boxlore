package cx.aswin.boxlore.feature.info.sections

import android.net.Uri
import android.text.Html
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogConfirmButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIcon
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIconTone
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.PodrollItem
import cx.aswin.boxlore.feature.info.R

internal fun stripHtml(html: String?): String {
    if (html.isNullOrEmpty()) return ""
    return android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
}

/**
 * The locked-feed line and its explanation. [dialogOpen] is saved, so the explanation stays open
 * through a fold that recreates the page; the show header holds it above its forms, so it also
 * stays open when a resize moves the description.
 */
@Composable
internal fun LockedFeedNotice(dialogOpen: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) }) {
    var showLockedInfoDialog by dialogOpen

    Spacer(modifier = Modifier.height(12.dp))
    androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(4.dp))

    // A full-height row, so the whole line is easy to tap and its press matches its shape.
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .expressiveClickable(shape = MaterialTheme.shapes.small) { showLockedInfoDialog = true }
            .padding(horizontal = 4.dp)
            .testTag(LOCKED_FEED_ROW_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = stringResource(R.string.podcast_info_locked_icon),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.podcast_info_locked_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.error,
        )
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = stringResource(R.string.podcast_info_locked_info_icon),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(14.dp),
        )
    }

    if (showLockedInfoDialog) {
        BoxloreAlertDialog(
            onDismissRequest = { showLockedInfoDialog = false },
            icon = { BoxloreDialogIcon(Icons.Rounded.Lock, BoxloreDialogIconTone.Caution) },
            title = { Text(stringResource(R.string.podcast_info_locked_title)) },
            text = { Text(stringResource(R.string.podcast_info_locked_body)) },
            confirmButton = { BoxloreDialogConfirmButton(stringResource(R.string.podcast_info_locked_ok), { showLockedInfoDialog = false }) },
        )
    }
}

@Composable
internal fun PodrollRecommendations(
    podroll: List<PodrollItem>,
    onPodcastClick: (String) -> Unit,
) {
    Spacer(modifier = Modifier.height(12.dp))
    androidx.compose.material3.HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(modifier = Modifier.height(12.dp))

    Text(
        text = stringResource(R.string.podcast_info_podroll_title),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = GoogleSansWeight.bold,
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

/**
 * The show's description card. It shows [collapsedLines] lines until tapped; when it hides more
 * (longer text, the creator's recommendations or the lock notice) an arrow at its end says so and
 * turns as it opens, and a closed card counts the creator's recommended shows.
 */
@Suppress("LongParameterList")
@Composable
internal fun PodcastInfoDescriptionSection(
    strippedDesc: String,
    isLocked: Boolean,
    podroll: List<PodrollItem>?,
    isDescExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPodcastClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    collapsedLines: Int = 2,
    lockedDialogOpen: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) },
) {
    val hasPodroll = !podroll.isNullOrEmpty()
    if (strippedDesc.isEmpty() && !isLocked && !hasPodroll) return
    // Whether the closed text runs past its lines; read from the drawn layout, reset with the text.
    var textOverflows by remember(strippedDesc, collapsedLines) { mutableStateOf(false) }
    // With no text, the lock notice and the creator's picks always show: nothing is hidden.
    val hidesMore = strippedDesc.isNotEmpty() && (textOverflows || hasPodroll || isLocked)
    // The arrow and its open/closed state show while the card hides more or is open over text.
    val showsCue = hidesMore || (isDescExpanded && strippedDesc.isNotEmpty())
    val expandedLabel = stringResource(R.string.branded_description_expanded)
    val collapsedLabel = stringResource(R.string.branded_description_collapsed)

    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ).expressiveClickable(shape = MaterialTheme.shapes.large) { onToggleExpanded() }
            .semantics { if (showsCue) stateDescription = if (isDescExpanded) expandedLabel else collapsedLabel }
            .testTag(DESCRIPTION_CARD_TAG),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Box {
            PodcastInfoDescriptionBody(
                strippedDesc = strippedDesc,
                isLocked = isLocked,
                podroll = podroll,
                hasPodroll = hasPodroll,
                isDescExpanded = isDescExpanded,
                onPodcastClick = onPodcastClick,
                collapsedLines = collapsedLines,
                onCollapsedOverflow = { textOverflows = it },
                lockedDialogOpen = lockedDialogOpen,
                endRoom = if (showsCue) DescriptionChevronRoom else 0.dp,
            )
            if (showsCue) DescriptionChevron(isDescExpanded, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** The card's arrow: down while it hides more, turning up as it opens; still with animations off. */
@Composable
private fun DescriptionChevron(expanded: Boolean, modifier: Modifier) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, ExpressiveMotion.QuickSpring, label = "descriptionChevron")
    Icon(
        Icons.Rounded.ExpandMore,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 16.dp, end = 12.dp).size(20.dp).graphicsLayer { rotationZ = rotation }.testTag(DESCRIPTION_CHEVRON_TAG),
    )
}

@Suppress("LongParameterList")
@Composable
internal fun PodcastInfoDescriptionBody(
    strippedDesc: String,
    isLocked: Boolean,
    podroll: List<PodrollItem>?,
    hasPodroll: Boolean,
    isDescExpanded: Boolean,
    onPodcastClick: (String) -> Unit,
    collapsedLines: Int = 2,
    onCollapsedOverflow: (Boolean) -> Unit = {},
    endRoom: Dp = 0.dp,
    lockedDialogOpen: MutableState<Boolean> = rememberSaveable { mutableStateOf(false) },
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
                maxLines = if (isDescExpanded) Int.MAX_VALUE else collapsedLines,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                onTextLayout = { if (!isDescExpanded) onCollapsedOverflow(it.hasVisualOverflow) },
                modifier = Modifier.fillMaxWidth().padding(end = endRoom),
            )
        }

        // With no description there's nothing to "expand" — always show the lock notice and
        // podroll in that case instead of hiding them behind a collapsed, empty description.
        val showMetadataRegardless = isDescExpanded || strippedDesc.isEmpty()
        if (showMetadataRegardless && isLocked) {
            LockedFeedNotice(lockedDialogOpen)
        }
        if (showMetadataRegardless && hasPodroll) {
            PodrollRecommendations(podroll = podroll.orEmpty(), onPodcastClick = onPodcastClick)
        } else if (hasPodroll) {
            PodrollCountLine(podroll.orEmpty().size)
        }
    }
}

/** On a closed card: how many shows the creator recommends, so listeners know the card holds them. */
@Composable
private fun PodrollCountLine(count: Int) {
    Row(
        modifier = Modifier.padding(top = 10.dp).testTag(PODROLL_COUNT_TAG),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.Podcasts, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            pluralStringResource(R.plurals.podcast_info_podroll_count, count, count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val DescriptionChevronRoom = 20.dp

internal const val DESCRIPTION_CARD_TAG = "podcast_info_description"
internal const val DESCRIPTION_CHEVRON_TAG = "podcast_info_description_chevron"
internal const val PODROLL_COUNT_TAG = "podcast_info_podroll_count"
internal const val LOCKED_FEED_ROW_TAG = "podcast_info_locked_row"

@Composable
internal fun RecommendedPodcastCard(
    item: cx.aswin.boxlore.core.model.PodrollItem,
    onPodcastClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
        modifier.boxloreCardWidth(BoxloreCardFamily.RailItem)
            .expressiveClickable(shape = MaterialTheme.shapes.medium) {
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
                fontWeight = GoogleSansWeight.medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
