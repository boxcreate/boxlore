package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.EngagementBottomSheetScaffold
import cx.aswin.boxlore.core.designsystem.layout.tabletSheetScroll
import cx.aswin.boxlore.core.designsystem.links.startFirstAvailable
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.home.R
import kotlinx.coroutines.launch

/**
 * Milestone review prompt — M3 Expressive bottom sheet.
 * Diverts happy users to Play Store, frustrated users to Feedback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewPromptSheet(
    completedCount: Int,
    onDismissRequest: () -> Unit,
    onNavigateToReview: () -> Unit,
    onNavigateToFeedback: () -> Unit,
) {
    TrackScreenView("review_prompt", send = {}) // the prompt is counted when it is decided
    val copy = reviewPromptCopy(completedCount)
    val title = copy.title.asString()
    val body = copy.body.asString()
    val primaryLabel = copy.primary.asString()
    val secondaryLabel = copy.secondary.asString()
    val onSecondaryClick = if (copy.secondaryDismisses) onDismissRequest else onNavigateToFeedback

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    EngagementBottomSheetScaffold(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        centeredDismissAllowed = true,
        showCloseButton = false,
        properties = ModalBottomSheetProperties(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().tabletSheetScroll(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
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
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onNavigateToReview,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ThumbUp,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = primaryLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSecondaryClick,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = secondaryLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

/** The milestone review prompt's words for [completedCount] finished episodes; at 30 the second button only closes it. */
internal data class ReviewPromptCopy(val title: UiText, val body: UiText, val primary: UiText, val secondary: UiText, val secondaryDismisses: Boolean)

internal fun reviewPromptCopy(completedCount: Int): ReviewPromptCopy = when {
    completedCount >= 30 -> ReviewPromptCopy(
        title = UiText.res(R.string.home_review_pro_title),
        body = UiText.plural(R.plurals.home_review_pro_body, completedCount, completedCount),
        primary = UiText.res(R.string.home_review_rate_boxlore),
        secondary = UiText.res(R.string.home_review_not_right_now),
        secondaryDismisses = true,
    )
    completedCount >= 15 -> ReviewPromptCopy(
        title = UiText.res(R.string.home_review_regular_title),
        body = UiText.plural(R.plurals.home_review_regular_body, completedCount, completedCount),
        primary = UiText.res(R.string.home_review_rate_boxlore),
        secondary = UiText.res(R.string.home_review_could_be_better),
        secondaryDismisses = false,
    )
    else -> ReviewPromptCopy(
        title = UiText.res(R.string.home_review_roll_title),
        body = UiText.plural(R.plurals.home_review_roll_body, completedCount, completedCount),
        primary = UiText.res(R.string.home_review_loving_it),
        secondary = UiText.res(R.string.home_review_could_be_better),
        secondaryDismisses = false,
    )
}

/**
 * Shown after the Play Store review flow returns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostReviewSheet(
    onDismissRequest: () -> Unit,
    onNavigateToFeedback: () -> Unit,
) {
    TrackScreenView("post_review")
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.home_post_review_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = GoogleSansWeight.semiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.home_post_review_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = onNavigateToFeedback,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.home_post_review_share_idea), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            val context = androidx.compose.ui.platform.LocalContext.current
            OutlinedButton(
                onClick = {
                    onDismissRequest()
                    cx.aswin.boxlore.core.analytics.AnalyticsHelper
                        .trackStoreReviewOpened(method = "store_page", source = "post_review")
                    val pkgName = context.packageName
                    context.startFirstAvailable(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkgName")),
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkgName")),
                    )
                },
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.home_review_rate_on_play), style = MaterialTheme.typography.labelLarge)
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onDismissRequest,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_post_review_maybe_later),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Feedback form — submits to Cloudflare Worker proxy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackSheet(
    appVersion: String,
    onSubmit: suspend (category: String, message: String, version: String, email: String) -> Boolean,
    onRateInstead: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    TrackScreenView("home_feedback")
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("feature") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isSuccess by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<Int?>(null) }

    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(isSuccess) {
        if (isSuccess) {
            kotlinx.coroutines.delay(1800)
            onDismissRequest()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier.imePadding(),
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_feedback_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.home_feedback_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isSuccess) {
                // Success state
                Box(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.ThumbUp,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.home_feedback_sent),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.semiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                // Category selector
                Text(
                    text = stringResource(R.string.home_feedback_category_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val categories =
                        listOf(
                            "feature" to stringResource(R.string.home_feedback_category_feature),
                            "bug" to stringResource(R.string.home_feedback_category_bug),
                            "other" to stringResource(R.string.home_feedback_category_other),
                        )

                    categories.forEach { (id, label) ->
                        FilterChip(
                            selected = selectedCategory == id,
                            onClick = { selectedCategory = id },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message input
                OutlinedTextField(
                    value = message,
                    onValueChange = { if (it.length <= FEEDBACK_MAX_LENGTH) message = it },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    placeholder = {
                        Text(
                            text =
                            when (selectedCategory) {
                                "feature" -> stringResource(R.string.home_feedback_hint_feature)
                                "bug" -> stringResource(R.string.home_feedback_hint_bug)
                                else -> stringResource(R.string.home_feedback_hint_other)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                // Character count
                Text(
                    text = stringResource(R.string.home_feedback_char_count, message.length, FEEDBACK_MAX_LENGTH),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.End,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text =
                    when (selectedCategory) {
                        "feature" -> stringResource(R.string.home_feedback_email_feature)
                        "bug" -> stringResource(R.string.home_feedback_email_bug)
                        else -> stringResource(R.string.home_feedback_email_other)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.labelMedium.lineHeight,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Email input (optional)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.home_feedback_email_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    singleLine = true,
                    colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                )

                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(errorMsg!!),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.home_feedback_anonymous),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Submit button
                Button(
                    onClick = {
                        if (message.isBlank()) {
                            errorMsg = R.string.home_feedback_error_empty
                            return@Button
                        }

                        isSubmitting = true
                        errorMsg = null

                        scope.launch {
                            val success = onSubmit(selectedCategory, message.trim(), appVersion, email.trim())
                            if (success) {
                                isSuccess = true
                            } else {
                                errorMsg = R.string.home_feedback_error_send
                                isSubmitting = false
                            }
                        }
                    },
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = !isSubmitting && message.isNotBlank(),
                    colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.home_feedback_sending), style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(stringResource(R.string.home_feedback_send), style = MaterialTheme.typography.labelLarge)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onRateInstead,
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.home_review_rate_on_play), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private const val FEEDBACK_MAX_LENGTH = 2000
