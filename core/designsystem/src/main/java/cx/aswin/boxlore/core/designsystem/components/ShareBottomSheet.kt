@file:Suppress("ktlint:standard:function-naming")

package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.components.BoxloreModalBottomSheet as ModalBottomSheet
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.share.ShareCardText
import cx.aswin.boxlore.core.designsystem.share.ShareManager
import cx.aswin.boxlore.core.designsystem.share.ShareMoment
import cx.aswin.boxlore.core.designsystem.share.formatShareTime
import cx.aswin.boxlore.core.designsystem.share.sharePreviewSizeDp
import cx.aswin.boxlore.core.designsystem.share.shareProgress
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.ShareLinkBuilder
import cx.aswin.boxlore.core.model.ShareTarget

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList")
fun ShareBottomSheet(
    id: String,
    type: String, // "podcast" or "episode"
    title: String,
    subtitle: String,
    imageUrl: String? = null,
    onDismissRequest: () -> Unit,
    durationMs: Long = 0L,
    currentPositionMs: Long = 0L,
    showTimestampOption: Boolean = false,
    /** The episode being shared; its copied link then names it the way [ShareManager] shares do. */
    episode: cx.aswin.boxlore.core.model.Episode? = null,
    onShare: (
        id: String,
        type: String,
        timestampMs: Long?,
        target: ShareTarget,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        ShareSheetBody(
            id = id,
            type = type,
            title = title,
            subtitle = subtitle,
            imageUrl = imageUrl,
            durationMs = durationMs,
            currentPositionMs = currentPositionMs,
            showTimestampOption = showTimestampOption,
            episode = episode,
            onShare = onShare,
            onDismissRequest = onDismissRequest,
            renderPreview = { card, progress ->
                ShareManager.renderPreview(context, card, progress, imageUrl, episode?.podcastImageUrl)?.asImageBitmap()
            },
        )
    }
}

/**
 * The share sheet's content: a preview of the card, the start-time switch for episodes and the
 * share actions. [renderPreview] draws the card; it runs again only when the card's words change,
 * not when the window resizes, rotates or folds.
 */
@Composable
@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
internal fun ShareSheetBody(
    id: String,
    type: String,
    title: String,
    subtitle: String,
    imageUrl: String?,
    durationMs: Long,
    currentPositionMs: Long,
    showTimestampOption: Boolean,
    episode: cx.aswin.boxlore.core.model.Episode?,
    onShare: (id: String, type: String, timestampMs: Long?, target: ShareTarget) -> Unit,
    onDismissRequest: () -> Unit,
    renderPreview: suspend (ShareCardText, Float) -> ImageBitmap?,
) {
    val context = LocalContext.current
    var includeTimestamp by rememberSaveable { mutableStateOf(false) }
    val contentLabel = if (type == "podcast") "podcast" else "episode"
    val latestPosition by rememberUpdatedState(currentPositionMs)
    val timestampOffered = type == "episode" && showTimestampOption && currentPositionMs > 1000L

    Column(
        modifier =
        Modifier
            .fillMaxWidth().tabletSheetScroll()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp).size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Share this $contentLabel",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Share an artwork card by message or story, or copy its link.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        val previewSize = sharePreviewSizeDp(LocalBoxloreWindowLayout.current.heightDp)
        val startsAt = includeTimestamp && timestampOffered
        val preview by produceState<ImageBitmap?>(initialValue = null, title, subtitle, imageUrl, startsAt, previewSize > 0) {
            value = null
            if (previewSize == 0) return@produceState
            val moment = if (startsAt) ShareMoment.StartsAt(latestPosition) else ShareMoment.Whole
            val card = ShareCardText(title, subtitle, ShareManager.shareMomentLabel(context, moment))
            value = renderPreview(card, shareProgress(moment, (durationMs / 1000L).toInt()))
        }
        if (previewSize > 0) {
            SharePreview(preview = preview, sizeDp = previewSize, description = stringResource(R.string.share_preview_description, title))
        } else {
            ShareSummaryRow(title = title, subtitle = subtitle, imageUrl = imageUrl)
        }

        if (timestampOffered) {
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                modifier =
                Modifier
                    .fillMaxWidth()
                    .expressiveClickable(
                        shape = MaterialTheme.shapes.extraLarge,
                        onClick = { includeTimestamp = !includeTimestamp },
                    ),
                shape = MaterialTheme.shapes.extraLarge,
                color =
                if (includeTimestamp) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            modifier = Modifier.padding(9.dp).size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Start at ${formatShareTime(currentPositionMs)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = GoogleSansWeight.bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Include your current position",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = includeTimestamp,
                        onCheckedChange = { includeTimestamp = it },
                        colors =
                        SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "CHOOSE HOW TO SHARE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = GoogleSansWeight.bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(10.dp))
        SharePrimaryAction(
            icon = Icons.Rounded.ChatBubble,
            title = "Send artwork card",
            subtitle = "Artwork and link",
            onClick = {
                val tMs =
                    if (includeTimestamp && showTimestampOption) {
                        currentPositionMs
                    } else {
                        null
                    }
                onShare(id, type, tMs, ShareTarget.MESSAGE)
                onDismissRequest()
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ShareSecondaryAction(
                icon = Icons.Rounded.ContentCopy,
                title = "Copy link",
                subtitle = "Link only",
                onClick = {
                    copyShareLink(context, id, type, episode, currentPositionMs.takeIf { includeTimestamp && showTimestampOption })
                    onDismissRequest()
                },
                modifier = Modifier.weight(1f),
            )
            ShareSecondaryAction(
                icon = Icons.Rounded.AutoAwesome,
                title = "Instagram Story",
                subtitle = "Artwork + copied link",
                onClick = {
                    val tMs =
                        if (includeTimestamp && showTimestampOption) {
                            currentPositionMs
                        } else {
                            null
                        }
                    onShare(id, type, tMs, ShareTarget.INSTAGRAM_STORY)
                    onDismissRequest()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Copies the link for [id]. An episode's link may first need its aliases and a registration, so
 * [cx.aswin.boxlore.core.designsystem.share.ShareManager] copies it in the background; any other link is copied at once.
 */
private fun copyShareLink(context: android.content.Context, id: String, type: String, episode: cx.aswin.boxlore.core.model.Episode?, timestampMs: Long?,) {
    if (episode != null && type == "episode") {
        cx.aswin.boxlore.core.designsystem.share.ShareManager.copyEpisodeLink(context, episode, timestampMs)
        return
    }
    val finalLink = ShareLinkBuilder.build(id = id, type = type, timestampMs = timestampMs)
    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("boxlore link", finalLink))
    android.widget.Toast.makeText(context, context.getString(R.string.share_link_copied), android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun SharePrimaryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
        modifier
            .fillMaxWidth()
            .expressiveClickable(
                shape = MaterialTheme.shapes.extraLarge,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun ShareSecondaryAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
        modifier
            .defaultMinSize(minHeight = 108.dp)
            .expressiveClickable(
                shape = MaterialTheme.shapes.extraLarge,
                onClick = onClick,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(19.dp),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = GoogleSansWeight.bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = subtitle,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The card as it will be sent, centred; a quiet placeholder of the same size while it draws. */
@Composable
private fun SharePreview(preview: ImageBitmap?, sizeDp: Int, description: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.size(sizeDp.dp).testTag(SharePreviewTag),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

/** Short windows show the artwork and name in one row instead of the full card. */
@Composable
private fun ShareSummaryRow(title: String, subtitle: String, imageUrl: String?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!imageUrl.isNullOrBlank()) {
                OptimizedImage(
                    url = imageUrl,
                    proxyWidth = 160,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.large),
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

internal const val SharePreviewTag = "share_preview"
