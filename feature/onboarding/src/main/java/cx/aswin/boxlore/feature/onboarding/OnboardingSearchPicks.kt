package cx.aswin.boxlore.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.artworkUrls

/**
 * The bar under the results once something is picked: a button with up to three of the picked
 * covers and the count, which opens them, and Continue. Opened from Echo, a note says the chat
 * carries on afterwards.
 */
@Composable
internal fun SearchPicksBar(picks: List<Podcast>, count: Int, isAiSideTrip: Boolean, onOpenPicks: () -> Unit, onDone: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag(SEARCH_PICKS_BAR_TAG)) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            if (isAiSideTrip) {
                Text(
                    text = stringResource(R.string.onboarding_search_side_trip_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SearchPicksButton(picks, count, onOpenPicks)
                OnboardingPrimaryButton(
                    label = pluralStringResource(
                        if (isAiSideTrip) R.plurals.onboarding_search_subscribe_continue_chat else R.plurals.onboarding_search_continue_with,
                        count,
                        count,
                    ),
                    enabled = true,
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Up to three picked covers overlapping, then "+N" for the rest; one button that opens the picks. */
@Composable
private fun SearchPicksButton(picks: List<Podcast>, count: Int, onClick: () -> Unit) {
    val label = pluralStringResource(R.plurals.onboarding_search_selected_shows, count, count)
    val shown = picks.take(PicksCoversShown)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.heightIn(min = 56.dp).testTag(SEARCH_PICKS_BUTTON_TAG).clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            onClick(label) {
                onClick()
                true
            }
        },
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                shown.forEachIndexed { index, podcast ->
                    OptimizedImage(
                        url = podcast.imageUrl,
                        fallbackUrls = podcast.artworkUrls(),
                        proxyWidth = 150,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(start = PicksCoverStep * index)
                            .size(PicksCoverSize)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
            if (count > shown.size) {
                Text(
                    "+${count - shown.size}", // i18n-ignore
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.bold,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp),
                )
            }
        }
    }
}

private const val PicksCoversShown = 3
private val PicksCoverSize = 36.dp
private val PicksCoverStep = 24.dp

/** The picked shows, each with its picked mark; tapping one removes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchPicksSheet(picks: List<Podcast>, onRemove: (Podcast) -> Unit, onDismiss: () -> Unit) {
    TrackScreenView("onboarding_search_picks")
    BoxloreModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().tabletSheetScroll().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).testTag(SEARCH_PICKS_SHEET_TAG)) {
            Text(
                pluralStringResource(R.plurals.onboarding_search_selected_shows, picks.size, picks.size),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp).semantics { heading() },
            )
            picks.forEach { podcast ->
                key(podcast.id) { SearchResultRow(podcast = podcast, isSubscribed = true, onSubscribe = { onRemove(podcast) }) }
            }
        }
    }
}

/** A typed result: the whole row is one checkbox, marked with Echo's badge. */
@Composable
internal fun SearchResultRow(podcast: Podcast, isSubscribed: Boolean, onSubscribe: () -> Unit) {
    val state = stringResource(if (isSubscribed) R.string.onboarding_search_selected else R.string.onboarding_search_select)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .semantics(mergeDescendants = true) {
                role = Role.Checkbox
                toggleableState = ToggleableState(isSubscribed)
                stateDescription = state
            }
            .expressiveClickable(onClick = onSubscribe)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OptimizedImage(
            url = podcast.imageUrl,
            fallbackUrls = podcast.artworkUrls(),
            proxyWidth = 400,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(podcast.title, style = MaterialTheme.typography.bodyLarge, fontWeight = GoogleSansWeight.medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                podcast.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        EchoSelectionBadge(selected = isSubscribed, icon = if (isSubscribed) Icons.Rounded.Check else Icons.Rounded.Add, size = 36.dp)
    }
}
