package cx.aswin.boxlore.feature.settings.feedback

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.prefs.BoxcastPrefs

private val FEEDBACK_CONTENT_BOTTOM_PADDING = 240.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    podcastRepository: PodcastRepository,
    boxcastPrefs: BoxcastPrefs,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val feedbackViewModel: FeedbackViewModel = viewModel(
        factory = FeedbackViewModel.Factory(podcastRepository, boxcastPrefs, context),
    )
    val uiState by feedbackViewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    Scaffold(
        modifier = modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Share your thoughts",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = GoogleSansWeight.semiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Navigate back",
                        )
                    }
                },
                actions = {
                    if (uiState.message.isNotBlank() || uiState.stepsToReproduce.isNotBlank()) {
                        IconButton(onClick = { feedbackViewModel.onDiscardDraft() }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Discard draft",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { paddingValues ->
        if (uiState.isSuccess) {
            FeedbackSuccessView(
                uiState = uiState,
                onDone = onBack,
                bottomPadding = FEEDBACK_CONTENT_BOTTOM_PADDING,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
            )
        } else {
            FeedbackFormView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        start = 20.dp,
                        top = 12.dp,
                        end = 20.dp,
                        bottom = FEEDBACK_CONTENT_BOTTOM_PADDING,
                    ),
                uiState = uiState,
                viewModel = feedbackViewModel,
                context = context,
            )
        }

        if (uiState.logsPreview != null || uiState.isLoadingLogs) {
            LogsPreviewDialog(
                logs = uiState.logsPreview,
                isLoading = uiState.isLoadingLogs,
                diagnosticInfo = uiState.diagnosticInfo,
                onShare = { shareFeedbackDiagnostics(context, uiState) },
                onDismissRequest = { feedbackViewModel.clearLogsPreview() },
            )
        }
    }
}

@Composable
private fun FeedbackFormView(
    modifier: Modifier = Modifier,
    uiState: FeedbackUiState,
    viewModel: FeedbackViewModel,
    context: Context,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        FeedbackDraftBanner(
            visible = uiState.isDraftRestored,
            onDiscard = { viewModel.onDiscardDraft() },
            onDismiss = { viewModel.onDismissDraftBanner() },
        )

        FeedbackCategorySelector(
            selectedCategory = uiState.category,
            onSelectCategory = { viewModel.onCategorySelected(it) },
        )

        FeedbackMessageInput(
            category = uiState.category,
            message = uiState.message,
            onMessageChanged = { viewModel.onMessageChanged(it) },
        )

        if (uiState.category.isBugReport) {
            FeedbackStepsInput(
                steps = uiState.stepsToReproduce,
                onStepsChanged = { viewModel.onStepsChanged(it) },
            )
        }

        FeedbackEmailInput(
            category = uiState.category,
            email = uiState.email,
            onEmailChanged = { viewModel.onEmailChanged(it) },
        )

        FeedbackDiagnosticsSection(
            attachDiagnostics = uiState.attachDiagnostics,
            diagnosticInfo = uiState.diagnosticInfo,
            onToggleAttach = { viewModel.onAttachDiagnosticsChanged(it) },
            onPreviewLogs = { viewModel.loadLogsForPreview() },
        )

        FeedbackCommunityRow(
            gitHubUrl = buildFeedbackGitHubIssueUrl(uiState),
            context = context,
        )

        FeedbackSubmitButton(
            isSubmitting = uiState.isSubmitting,
            message = uiState.message,
            errorMessage = uiState.errorMessage,
            onSubmit = { viewModel.onSubmit() },
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FeedbackDraftBanner(
    visible: Boolean,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
            shape = MaterialTheme.shapes.medium,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Restored draft from last session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onDiscard) {
                    Text(
                        text = "Discard",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Dismiss banner",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedbackCategorySelector(
    selectedCategory: FeedbackCategory,
    onSelectCategory: (FeedbackCategory) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeedbackCategory.entries.forEach { cat ->
                val selected = selectedCategory == cat
                FilterChip(
                    selected = selected,
                    onClick = { onSelectCategory(cat) },
                    leadingIcon = {
                        val icon = when (cat) {
                            FeedbackCategory.FEATURE -> Icons.Rounded.Lightbulb
                            FeedbackCategory.BUG -> Icons.Rounded.BugReport
                            FeedbackCategory.AUDIO -> Icons.Rounded.Headphones
                            FeedbackCategory.OTHER -> Icons.AutoMirrored.Rounded.Message
                        }
                        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = { Text(cat.label) },
                    shape = MaterialTheme.shapes.medium,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }
    }
}

@Composable
private fun FeedbackMessageInput(
    category: FeedbackCategory,
    message: String,
    onMessageChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            placeholder = {
                Text(
                    text = category.placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )

        Text(
            text = "${message.length}/2000",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun FeedbackStepsInput(
    steps: String,
    onStepsChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Steps to Reproduce (Optional)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Tip: Reproduce the issue right before sending so recent logs capture the error.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        OutlinedTextField(
            value = steps,
            onValueChange = onStepsChanged,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            placeholder = {
                Text(
                    text = "1. Play episode X\n2. At 02:15 audio stops\n3. Happens on both Wi-Fi and Cellular",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

private fun getEmailSupportingText(category: FeedbackCategory): String = when (category) {
    FeedbackCategory.BUG, FeedbackCategory.AUDIO ->
        "We obsess over getting boxlore right. Rather than applying a quick band-aid, we would like to chat more about the issue to ensure that it is truly solved. So please, if possible, share your email — we promise to respect your inbox with no marketing, only direct help on this issue."
    FeedbackCategory.FEATURE ->
        "We love shaping boxlore alongside our listeners. If your idea sparks something great, we’d love to reach out to brainstorm, discuss designs, or have you test it early — strictly product chats, zero marketing."
    FeedbackCategory.OTHER ->
        "Every note helps shape where boxlore goes next. Leave your email if you'd like us to write back — we read every single message and promise to respect your inbox with zero marketing."
}

@Composable
private fun FeedbackEmailInput(
    category: FeedbackCategory,
    email: String,
    onEmailChanged: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "Contact Email (Optional)",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = GoogleSansWeight.medium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "you@example.com",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            },
            supportingText = {
                Text(
                    text = getEmailSupportingText(category),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            },
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            textStyle = MaterialTheme.typography.bodyMedium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun FeedbackDiagnosticsSection(
    attachDiagnostics: Boolean,
    diagnosticInfo: DiagnosticInfo?,
    onToggleAttach: (Boolean) -> Unit,
    onPreviewLogs: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include device diagnostics",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = GoogleSansWeight.medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Helps troubleshoot hardware, OS, and network issues",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = attachDiagnostics,
                    onCheckedChange = onToggleAttach,
                )
            }

            if (attachDiagnostics && diagnosticInfo != null) {
                DiagnosticsDetailsContent(
                    diagnosticInfo = diagnosticInfo,
                    onPreviewLogs = onPreviewLogs,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsDetailsContent(
    diagnosticInfo: DiagnosticInfo,
    onPreviewLogs: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "boxlore v${diagnosticInfo.appVersion} (${diagnosticInfo.buildCode}) • Android ${diagnosticInfo.androidRelease} (API ${diagnosticInfo.sdkInt})",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${diagnosticInfo.manufacturer} ${diagnosticInfo.model} • ${diagnosticInfo.networkType} • ${diagnosticInfo.audioRoute}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "Included automatically with report",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        TextButton(
            onClick = onPreviewLogs,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Visibility,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Preview logs",
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun FeedbackCommunityRow(
    gitHubUrl: String,
    context: Context,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(gitHubUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Launch,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("GitHub Issue", maxLines = 1)
        }

        OutlinedButton(
            onClick = {
                val pkgName = context.packageName
                val uri = try {
                    Uri.parse("market://details?id=$pkgName")
                } catch (_: Exception) {
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkgName")
                }
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Rate on Play", maxLines = 1)
        }
    }
}

@Composable
private fun FeedbackSubmitButton(
    isSubmitting: Boolean,
    message: String,
    errorMessage: String?,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Button(
            onClick = onSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = !isSubmitting && message.isNotBlank(),
            shape = MaterialTheme.shapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = "Send Feedback",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                )
            }
        }
    }
}
