package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsIconContainer
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold

private const val GroqOnboardingModel = "openai/gpt-oss-120b"

internal data class PrivacySettingsActions(
    val onDeletionExpandedChange: (Boolean) -> Unit,
    val onResetIdentityClick: () -> Unit,
    val onResetRecommendationsClick: () -> Unit,
    val onCopyDeletionId: () -> Unit,
    val onEmailDeletionRequest: () -> Unit,
)

@Composable
internal fun PrivacySettingsPage(
    deletionId: String,
    isDeletionExpanded: Boolean,
    actions: PrivacySettingsActions,
    onBack: () -> Unit,
) {
    TrackScreenView("privacy")
    SettingsScaffold(
        title = stringResource(R.string.settings_privacy_title),
        onBack = onBack,
    ) {
        PromiseCard()

        SettingsGroup(title = stringResource(R.string.settings_privacy_sketch_title)) {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text =
                        stringResource(R.string.settings_privacy_sketch_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text =
                        stringResource(R.string.settings_privacy_sketch_typed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy_sketch_never),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = GoogleSansWeight.medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_privacy_app_use_title)) {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Analytics,
                collected =
                stringResource(R.string.settings_privacy_app_use_collected),
                reason =
                stringResource(R.string.settings_privacy_app_use_reason),
                example =
                stringResource(R.string.settings_privacy_app_use_example),
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_privacy_search_title)) {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Search,
                collected =
                stringResource(R.string.settings_privacy_search_collected, GroqOnboardingModel),
                reason =
                stringResource(R.string.settings_privacy_search_reason),
                example =
                stringResource(R.string.settings_privacy_search_example),
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_privacy_listening_title)) {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Podcasts,
                collected =
                stringResource(R.string.settings_privacy_listening_collected),
                reason =
                stringResource(R.string.settings_privacy_listening_reason),
                example = null,
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_privacy_device_title)) {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Devices,
                collected =
                stringResource(R.string.settings_privacy_device_collected),
                reason =
                stringResource(R.string.settings_privacy_device_reason),
                example = null,
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_privacy_crashes_title)) {
            PrivacyCategoryContent(
                icon = Icons.Rounded.BugReport,
                collected = stringResource(R.string.settings_privacy_crashes_collected),
                reason = stringResource(R.string.settings_privacy_crashes_reason),
                example = null,
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_privacy_support_title)) {
            PrivacyCategoryContent(
                icon = Icons.Rounded.VolunteerActivism,
                collected =
                stringResource(R.string.settings_privacy_support_collected),
                reason =
                stringResource(R.string.settings_privacy_support_reason),
                example = null,
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_privacy_feedback_title)) {
            PrivacyCategoryContent(
                icon = Icons.Rounded.Feedback,
                collected =
                stringResource(R.string.settings_privacy_feedback_collected),
                reason =
                stringResource(R.string.settings_privacy_feedback_reason),
                example = null,
            )
        }

        SettingsGroup(
            title = stringResource(R.string.settings_privacy_on_device_title),
            footer =
            stringResource(R.string.settings_privacy_on_device_footer),
        ) {
            SettingsContent {
                AnalyticsControlCard(
                    title = stringResource(R.string.settings_privacy_reset_recs_title),
                    body =
                    stringResource(R.string.settings_privacy_reset_recs_body),
                    icon = Icons.Rounded.Refresh,
                    destructive = false,
                    actionLabel = stringResource(R.string.settings_privacy_reset_recs_action),
                    onAction = actions.onResetRecommendationsClick,
                    expansion = null,
                )
            }
        }

        SettingsGroup(
            title = stringResource(R.string.settings_privacy_analytics_id_title),
            footer =
            stringResource(R.string.settings_privacy_analytics_id_footer),
        ) {
            SettingsContent {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnalyticsControlCard(
                        title = stringResource(R.string.settings_privacy_new_id_title),
                        body = stringResource(R.string.settings_privacy_new_id_body),
                        icon = Icons.Rounded.Refresh,
                        destructive = false,
                        actionLabel = stringResource(R.string.settings_privacy_new_id_action),
                        onAction = actions.onResetIdentityClick,
                        expansion = null,
                    )

                    AnalyticsControlCard(
                        title = stringResource(R.string.settings_privacy_deletion_title),
                        body = stringResource(R.string.settings_privacy_deletion_body),
                        icon = Icons.Rounded.DeleteForever,
                        destructive = true,
                        actionLabel = if (isDeletionExpanded) stringResource(R.string.settings_privacy_deletion_hide) else stringResource(R.string.settings_privacy_deletion_show),
                        onAction = {
                            actions.onDeletionExpandedChange(!isDeletionExpanded)
                        },
                        expansion =
                        AnalyticsCardExpansion(
                            expanded = isDeletionExpanded,
                            onToggleExpand = {
                                actions.onDeletionExpandedChange(!isDeletionExpanded)
                            },
                            content = {
                                DeletionRequestPanel(
                                    deletionId = deletionId,
                                    onCopyDeletionId = actions.onCopyDeletionId,
                                    onEmailDeletionRequest = actions.onEmailDeletionRequest,
                                )
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PromiseCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SettingsIconContainer(
                    icon = Icons.Rounded.VolunteerActivism,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    size = 48.dp,
                    shape = MaterialTheme.shapes.large,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.settings_privacy_promise_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = GoogleSansWeight.bold,
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy_promise_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }

            Text(
                text =
                stringResource(R.string.settings_privacy_promise_body),
                style = MaterialTheme.typography.bodyLarge,
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.14f),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.settings_privacy_promise_means),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.bold,
                )
                PromisePillRow(
                    pills =
                    listOf(
                        stringResource(R.string.settings_privacy_pill_never_sold),
                        stringResource(R.string.settings_privacy_pill_never_ads),
                        stringResource(R.string.settings_privacy_pill_no_ads),
                        stringResource(R.string.settings_privacy_pill_usage_only),
                    ),
                )
                Text(
                    text =
                    stringResource(R.string.settings_privacy_promise_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.92f),
                )
            }
        }
    }
}

/** Test tag on each promise pill. */
internal const val PRIVACY_PROMISE_PILL_TAG = "privacy_promise_pill"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PromisePillRow(pills: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        pills.forEach { label ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(PRIVACY_PROMISE_PILL_TAG),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                )
            }
        }
    }
}

@Composable
private fun PrivacyCategoryContent(
    icon: ImageVector,
    collected: String,
    reason: String,
    example: String?,
) {
    SettingsContent {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsIconContainer(
                icon = icon,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )

            PrivacyField(
                label = stringResource(R.string.settings_privacy_field_collected),
                body = collected,
            )
            PrivacyField(
                label = stringResource(R.string.settings_privacy_field_why),
                body = reason,
            )

            if (example != null) {
                ExampleCallout(text = example)
            }
        }
    }
}

@Composable
private fun PrivacyField(
    label: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = GoogleSansWeight.bold,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExampleCallout(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lightbulb,
                contentDescription = null,
                modifier =
                Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.settings_privacy_example_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.bold,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Expand/collapse affordance for [AnalyticsControlCard]; omitted entirely for non-expandable cards. */
private data class AnalyticsCardExpansion(
    val expanded: Boolean,
    val onToggleExpand: () -> Unit,
    val content: @Composable () -> Unit,
)

internal data class AnalyticsCardColors(
    val container: Color,
    val onContainer: Color,
    val iconContainer: Color,
    val iconContent: Color,
)

@Composable
internal fun analyticsCardColors(destructive: Boolean): AnalyticsCardColors = if (destructive) {
    AnalyticsCardColors(
        container = MaterialTheme.colorScheme.errorContainer,
        onContainer = MaterialTheme.colorScheme.onErrorContainer,
        iconContainer = MaterialTheme.colorScheme.error,
        iconContent = MaterialTheme.colorScheme.onError,
    )
} else {
    AnalyticsCardColors(
        container = MaterialTheme.colorScheme.surface,
        onContainer = MaterialTheme.colorScheme.onSurface,
        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
        iconContent = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}

@Composable
private fun AnalyticsCardActionButton(
    destructive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    if (destructive) {
        Button(
            onClick = onAction,
            colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text(actionLabel)
        }
    } else {
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

@Composable
private fun AnalyticsCardHeader(
    title: String,
    body: String,
    icon: ImageVector,
    colors: AnalyticsCardColors,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsIconContainer(
                icon = icon,
                containerColor = colors.iconContainer,
                contentColor = colors.iconContent,
            )
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = GoogleSansWeight.semiBold,
            )
        }
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onContainer.copy(alpha = 0.85f),
        )
    }
}

@Composable
private fun AnalyticsCardFooter(
    destructive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    expansion: AnalyticsCardExpansion?,
    onContainer: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expansion != null) {
            Icon(
                imageVector =
                if (expansion.expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = null,
                tint = onContainer.copy(alpha = 0.7f),
            )
        }
        AnalyticsCardActionButton(
            destructive = destructive,
            actionLabel = actionLabel,
            onAction = onAction,
        )
    }
}

@Composable
private fun AnalyticsControlCard(
    title: String,
    body: String,
    icon: ImageVector,
    destructive: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    expansion: AnalyticsCardExpansion?,
) {
    val colors = analyticsCardColors(destructive)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.container,
        contentColor = colors.onContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnalyticsCardHeader(title = title, body = body, icon = icon, colors = colors)
            AnalyticsCardFooter(
                destructive = destructive,
                actionLabel = actionLabel,
                onAction = onAction,
                expansion = expansion,
                onContainer = colors.onContainer,
            )
            if (expansion != null) {
                AnimatedVisibility(visible = expansion.expanded) {
                    Column {
                        Spacer(modifier = Modifier.height(4.dp))
                        expansion.content()
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletionRequestPanel(
    deletionId: String,
    onCopyDeletionId: () -> Unit,
    onEmailDeletionRequest: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.settings_privacy_deletion_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f),
        )
        Surface(
            modifier =
            Modifier
                .fillMaxWidth()
                .expressiveClickable(
                    shape = MaterialTheme.shapes.small,
                    onClick = onCopyDeletionId,
                ),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = deletionId,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = stringResource(R.string.settings_privacy_copy_id),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onEmailDeletionRequest,
            modifier = Modifier.fillMaxWidth(),
            colors =
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(
                imageVector = Icons.Rounded.Email,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.settings_privacy_open_email),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
