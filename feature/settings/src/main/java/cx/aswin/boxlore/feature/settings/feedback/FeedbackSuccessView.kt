package cx.aswin.boxlore.feature.settings.feedback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

internal data class FeedbackSuccessContent(
    val title: String,
    val subtitle: String,
    val statusTitle: String,
    val statusDescription: String,
    val isEmailAttached: Boolean,
)

internal fun getFeedbackSuccessContent(
    category: FeedbackCategory,
    email: String,
): FeedbackSuccessContent {
    val trimmedEmail = email.trim()
    val hasEmail = trimmedEmail.isNotBlank()

    return when (category) {
        FeedbackCategory.BUG -> FeedbackSuccessContent(
            title = "Bug report submitted",
            subtitle = "Thank you for helping us squash this bug. We obsess over getting boxlore right and fixing issues at the source.",
            statusTitle = if (hasEmail) "Reach out within 24 hours" else "Submitted anonymously",
            statusDescription = if (hasEmail) {
                "Rather than applying a quick band-aid, we would like to chat more about the issue to ensure it is truly solved. We will review your diagnostics and reach out to $trimmedEmail within 24 hours."
            } else {
                "Your report has been logged in our debugging queue. We will investigate the root cause for upcoming boxlore updates."
            },
            isEmailAttached = hasEmail,
        )
        FeedbackCategory.AUDIO -> FeedbackSuccessContent(
            title = "Audio report submitted",
            subtitle = "Playback reliability is core to boxlore. Thank you for reporting this stream behavior so we can investigate and resolve it.",
            statusTitle = if (hasEmail) "Reach out within 24 hours" else "Submitted anonymously",
            statusDescription = if (hasEmail) {
                "We obsess over getting boxlore right. We'll analyze your playback diagnostics and reach out to $trimmedEmail within 24 hours to help troubleshoot."
            } else {
                "Your audio playback report and stream diagnostics have been queued for investigation in our next release."
            },
            isEmailAttached = hasEmail,
        )
        FeedbackCategory.FEATURE -> FeedbackSuccessContent(
            title = "Feature idea submitted",
            subtitle = "We love shaping boxlore alongside our listeners. Your suggestion helps steer what we build next.",
            statusTitle = if (hasEmail) "Reach out within 24 hours" else "Submitted anonymously",
            statusDescription = if (hasEmail) {
                "We discuss community ideas regularly. If we explore this feature further or need your perspective, we'll reach out to $trimmedEmail within 24 hours."
            } else {
                "Your idea has been added to our product design roadmap for upcoming boxlore planning."
            },
            isEmailAttached = hasEmail,
        )
        FeedbackCategory.OTHER -> FeedbackSuccessContent(
            title = "Feedback received",
            subtitle = "Thank you for taking the time to write to us. Every note and perspective helps make boxlore better.",
            statusTitle = if (hasEmail) "Reach out within 24 hours" else "Submitted anonymously",
            statusDescription = if (hasEmail) {
                "We personally review every note. If your message requires a follow-up or reply, we'll reach out to $trimmedEmail within 24 hours."
            } else {
                "Your feedback has been safely shared with the boxlore team. Thank you for listening!"
            },
            isEmailAttached = hasEmail,
        )
    }
}

@Composable
fun FeedbackSuccessView(
    uiState: FeedbackUiState,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content = remember(uiState.category, uiState.email) {
        getFeedbackSuccessContent(uiState.category, uiState.email)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        FeedbackSuccessHero(title = content.title, subtitle = content.subtitle)
        Spacer(modifier = Modifier.height(28.dp))
        FeedbackStatusCard(content = content)
        Spacer(modifier = Modifier.height(16.dp))
        FeedbackSummaryCard(uiState = uiState, isEmailAttached = content.isEmailAttached)
        Spacer(modifier = Modifier.height(36.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Text("Done")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FeedbackSuccessHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = GoogleSansWeight.semiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

private val FeedbackCategory.icon: ImageVector
    get() = when (this) {
        FeedbackCategory.BUG -> Icons.Rounded.BugReport
        FeedbackCategory.AUDIO -> Icons.Rounded.Headphones
        FeedbackCategory.FEATURE -> Icons.Rounded.Lightbulb
        FeedbackCategory.OTHER -> Icons.AutoMirrored.Rounded.Message
    }

@Composable
private fun FeedbackStatusCard(
    content: FeedbackSuccessContent,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (content.isEmailAttached) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = if (content.isEmailAttached) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (content.isEmailAttached) {
                            Icons.Rounded.Schedule
                        } else {
                            Icons.Rounded.DoneAll
                        },
                        contentDescription = null,
                        tint = if (content.isEmailAttached) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = content.statusTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = content.statusDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun FeedbackSummaryCard(
    uiState: FeedbackUiState,
    isEmailAttached: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCategoryRow(category = uiState.category)

            if (isEmailAttached) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                SummaryEmailRow(email = uiState.email.trim())
            }

            if (uiState.attachDiagnostics) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                SummaryDiagnosticsRow()
            }
        }
    }
}

@Composable
private fun SummaryCategoryRow(category: FeedbackCategory) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Category",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Text(
                text = category.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SummaryEmailRow(email: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Email,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Contact email",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = email,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = GoogleSansWeight.medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SummaryDiagnosticsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Analytics,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Diagnostics & logs",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "Included",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = GoogleSansWeight.medium,
                )
            }
        }
    }
}
