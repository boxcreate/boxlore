package cx.aswin.boxlore.feature.settings.feedback

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import cx.aswin.boxlore.core.designsystem.echo.EchoCharacter
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.prefs.BoxcastPrefs

internal const val FEEDBACK_SENT_TAG = "feedback_sent"
internal const val FEEDBACK_NOTIFY_CARD_TAG = "feedback_notify_card"

/** Echo looks through the magnifier once, then rests. */
internal const val FEEDBACK_SENT_ECHO = "feedback-sent"

/** The reply promise only when the message became a conversation; the old form has no replies in the app. */
internal fun feedbackSentBody(repliesInApp: Boolean): String = if (repliesInApp) {
    "Thanks for telling us. The boxlore team reads every message, and the reply will show up in Feedback."
} else {
    "Thanks for telling us. The boxlore team reads every message."
}

/**
 * After sending: Echo, a thank-you, and (when notifications are off) a card to turn them on so the
 * listener hears about the team's reply. A message sent through the old one-way form gets neither. The card re-checks when the listener comes back from settings.
 */
@Composable
internal fun FeedbackSentScreen(
    boxcastPrefs: BoxcastPrefs,
    repliesInApp: Boolean,
    onDone: () -> Unit,
    bottomContentPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(areAppNotificationsEnabled(context)) }
    var dismissed by rememberSaveable { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notificationsOn = areAppNotificationsEnabled(context)
        onPauseOrDispose { }
    }
    val askLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = areAppNotificationsEnabled(context)
    }
    val turnOn = {
        if (canAskForNotifications(context, boxcastPrefs.hasRequestedNotificationPermission())) {
            boxcastPrefs.setHasRequestedNotificationPermission()
            askLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openAppNotificationSettings(context)
        }
    }
    val systemBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Surface(modifier = modifier.fillMaxSize().testTag(FEEDBACK_SENT_TAG), color = MaterialTheme.colorScheme.surface) {
        Box(
            modifier = Modifier.fillMaxSize().statusBarsPadding().padding(bottom = bottomContentPadding + systemBar),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(Modifier.fillMaxSize().boxloreCardWidth(BoxloreCardFamily.Utility)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                    FeedbackIconButton(Icons.Rounded.Close, "Close", onDone)
                }
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EchoCharacter(sequence = FEEDBACK_SENT_ECHO, modifier = Modifier.size(width = 168.dp, height = 228.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Feedback sent", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        feedbackSentBody(repliesInApp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    if (repliesInApp && !notificationsOn && !dismissed) {
                        Spacer(Modifier.height(28.dp))
                        NotificationCard(onTurnOn = turnOn, onNotNow = { dismissed = true })
                    }
                    Spacer(Modifier.height(16.dp))
                }
                FilledTonalButton(
                    onClick = onDone,
                    shape = CircleShape,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().height(64.dp),
                ) {
                    Text("Done", style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(onTurnOn: () -> Unit, onNotNow: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().testTag(FEEDBACK_NOTIFY_CARD_TAG),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
                    Icon(Icons.Rounded.NotificationsNone, contentDescription = null, modifier = Modifier.padding(11.dp).size(22.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Know when the team replies", style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold)
                    Text(
                        "Replies can take a day or two. Turn on notifications so you don't miss one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTurnOn, shape = CircleShape, modifier = Modifier.weight(1f).heightIn(min = 48.dp), colors = ButtonDefaults.buttonColors()) {
                    Text("Turn on notifications", fontWeight = GoogleSansWeight.semiBold)
                }
                TextButton(onClick = onNotNow, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("Not now", fontWeight = GoogleSansWeight.semiBold)
                }
            }
        }
    }
}
