package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TonalToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.contrastColor
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.R

/**
 * Episode Toolbar - M3 Expressive
 * Contains: Search, Sort Toggle, Subscribe Button
 */
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EpisodeToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    isSearching: Boolean,
    currentSort: EpisodeSort,
    onSortToggle: () -> Unit,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit,
    accentColor: Color,
    supportsReleaseAutomation: Boolean = true,
    notificationsEnabled: Boolean = false,
    isSystemNotificationsBlocked: Boolean = false,
    onNotificationsToggle: () -> Unit = {},
    autoDownloadEnabled: Boolean = false,
    onAutoDownloadToggle: () -> Unit = {},
    genre: String = "",
    onSearchFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val subscribeLabel = stringResource(R.string.podcast_info_subscribe)
    val subscribedLabel = stringResource(R.string.podcast_info_subscribed)
    val unsubscribeLabel = stringResource(R.string.podcast_info_unsubscribe)

    val phase = rememberSubscriptionButtonPhase(isSubscribed)
    val automationFraction by animateFloatAsState(
        targetValue = if (isSubscribed && supportsReleaseAutomation) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = if (isSubscribed) 420f else 560f,
        ),
        label = "subscription_settings_reveal",
    )
    val automationReveal = subscriptionAutomationReveal(automationFraction)
    val automationEnabled = isSubscribed && supportsReleaseAutomation && automationReveal.fullyVisible
    val sortRotation by animateFloatAsState(
        targetValue = if (currentSort == EpisodeSort.NEWEST) 0f else 180f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "sortRotation",
    )
    val containerColor by animateColorAsState(
        targetValue = if (phase == SubscriptionButtonPhase.SUBSCRIBED) MaterialTheme.colorScheme.surfaceContainerHighest else accentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "subscribeContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (phase == SubscriptionButtonPhase.SUBSCRIBED) MaterialTheme.colorScheme.onSurface else accentColor.contrastColor(),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "subscribeContent",
    )

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onSubscribeClick,
                shapes = ButtonDefaults.shapes(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 24.dp),
                    pressedShape = RoundedCornerShape(18.dp),
                ),
                colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
                contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 12.dp),
                modifier = Modifier.weight(1f).height(48.dp).semantics {
                    contentDescription = if (isSubscribed) unsubscribeLabel else subscribeLabel
                },
            ) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = {
                        val enter = if (targetState == SubscriptionButtonPhase.CONFIRMATION) {
                            fadeIn(tween(120))
                        } else {
                            fadeIn(tween(160, delayMillis = 60)) + scaleIn(tween(200), initialScale = 0.92f)
                        }
                        enter
                            .togetherWith(fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.96f))
                            .using(SizeTransform(clip = false))
                    },
                    contentAlignment = Alignment.Center,
                    label = "subContent",
                ) { phase ->
                    when (phase) {
                        SubscriptionButtonPhase.CONFIRMATION -> SubscriptionGenreConfirmation(genre)
                        else -> Text(
                            text = if (phase == SubscriptionButtonPhase.SUBSCRIBED) subscribedLabel else subscribeLabel,
                            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                            fontWeight = GoogleSansWeight.bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            SubscriptionAutomationSlot(automationReveal, automationEnabled) {
                SubscriptionToggle(
                    checked = notificationsEnabled,
                    icon = if (notificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                    label = stringResource(R.string.podcast_info_notifications),
                    onClick = onNotificationsToggle,
                    blocked = notificationsEnabled && isSystemNotificationsBlocked,
                    enabled = automationEnabled,
                    modifier = Modifier.graphicsLayer {
                        alpha = automationReveal.bellAlpha
                        scaleX = 0.94f + 0.06f * automationReveal.bellAlpha
                        scaleY = scaleX
                    },
                )
                SubscriptionToggle(
                    checked = autoDownloadEnabled,
                    icon = ImageVector.vectorResource(R.drawable.ic_cloud_download),
                    label = stringResource(R.string.podcast_info_auto_download),
                    onClick = onAutoDownloadToggle,
                    enabled = automationEnabled,
                    modifier = Modifier.graphicsLayer {
                        alpha = automationReveal.downloadAlpha
                        scaleX = 0.94f + 0.06f * automationReveal.downloadAlpha
                        scaleY = scaleX
                    },
                )
            }
            ConnectedToolbarAction(
                icon = Icons.AutoMirrored.Rounded.Sort,
                label = stringResource(R.string.podcast_info_sort),
                onClick = onSortToggle,
                modifier = Modifier.padding(start = 3.dp).graphicsLayer { rotationX = sortRotation },
            )
            ConnectedToolbarAction(
                icon = Icons.Rounded.Search,
                label = stringResource(R.string.podcast_info_search),
                onClick = onSearchFocused,
                modifier = Modifier.padding(start = 3.dp),
                trailing = true,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ConnectedToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: Boolean = false,
) {
    Button(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(
            shape = if (trailing) {
                RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 8.dp)
            } else {
                RoundedCornerShape(8.dp)
            },
            pressedShape = RoundedCornerShape(18.dp),
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(48.dp).semantics { contentDescription = label },
    ) {
        Icon(icon, null, Modifier.size(21.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SubscriptionToggle(
    checked: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    blocked: Boolean = false,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val blockedLabel = stringResource(R.string.podcast_info_notifications_blocked)
    val shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(
        shape = RoundedCornerShape(8.dp),
        pressedShape = RoundedCornerShape(18.dp),
    )
    TonalToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        enabled = enabled,
        shapes = shapes,
        colors = ToggleButtonDefaults.tonalToggleButtonColors(
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurfaceVariant,
            checkedContainerColor = if (blocked) scheme.errorContainer else scheme.primaryContainer,
            checkedContentColor = if (blocked) scheme.onErrorContainer else scheme.onPrimaryContainer,
            disabledContainerColor = if (checked) {
                if (blocked) scheme.errorContainer else scheme.primaryContainer
            } else {
                scheme.surfaceContainerHighest
            },
            disabledContentColor = if (checked) {
                if (blocked) scheme.onErrorContainer else scheme.onPrimaryContainer
            } else {
                scheme.onSurfaceVariant
            },
        ),
        contentPadding = PaddingValues(0.dp),
        modifier = modifier.size(48.dp).semantics {
            contentDescription = label
            if (blocked) stateDescription = blockedLabel
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp))
            if (blocked) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(6.dp)
                        .background(scheme.error, CircleShape),
                )
            }
        }
    }
}
