package cx.aswin.boxlore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.R
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreFoldSafeViewport
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme

/** A restored startup decision survives resize; acknowledging never completes onboarding. */
internal fun shouldShowTabletAlphaNotice(window: BoxloreWindowLayout, acknowledged: Boolean, restoredPending: Boolean? = null): Boolean =
    !acknowledged && (restoredPending ?: window.usesTabletLayout)

/** The app subtree, including Welcome and separate sign-in UI, is absent until acknowledgment. */
@Composable
internal fun TabletAlphaStartupGate(
    pending: Boolean,
    onAcknowledge: () -> Unit,
    noticeTheme: @Composable (@Composable () -> Unit) -> Unit = { BoxLoreTheme(content = it) },
    content: @Composable () -> Unit,
) {
    if (pending) noticeTheme { TabletAlphaNoticeScreen(onAcknowledge) } else content()
}

@Composable
private fun TabletAlphaNoticeScreen(onAcknowledge: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag("tablet_alpha_notice"), color = MaterialTheme.colorScheme.surface.copy(alpha = 1f)) {
        BoxWithConstraints(Modifier.fillMaxSize().boxloreFoldSafeViewport().safeDrawingPadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.TabletAndroid, null, Modifier.size(36.dp).rotate(90f)) }
                    }
                    Text(stringResource(R.string.tablet_alpha_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.tablet_alpha_body), style = MaterialTheme.typography.bodyLarge)
                    TabletFeedbackInstruction()
                }
                Button(onClick = onAcknowledge, modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp).testTag("tablet_alpha_ok")) {
                    Text(stringResource(R.string.tablet_alpha_ok))
                }
            }
        }
    }
}

@Composable
private fun TabletFeedbackInstruction() {
    val marker = "\uFFFC"
    val instruction = stringResource(R.string.tablet_alpha_feedback_instruction, marker)
    val markerIndex = instruction.indexOf(marker)
    val text = buildAnnotatedString {
        append(instruction.substring(0, markerIndex))
        appendInlineContent("feedback", stringResource(R.string.tablet_alpha_feedback_icon_name))
        append(instruction.substring(markerIndex + marker.length))
    }
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        inlineContent = mapOf(
            "feedback" to InlineTextContent(Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)) {
            Icon(Icons.Rounded.Feedback, contentDescription = null, modifier = Modifier.fillMaxSize())
            },
        ),
    )
}
