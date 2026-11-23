package cx.aswin.boxlore.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.PlaybackPlaceNotice
import cx.aswin.boxlore.core.designsystem.components.RepairProgressPopup
import cx.aswin.boxlore.core.designsystem.components.SleepTimerPopup
import cx.aswin.boxlore.core.designsystem.components.SleepTimerPopupDismissReason
import cx.aswin.boxlore.core.designsystem.layout.boxloreFoldSafeViewport
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.dismissLateNightNudge
import cx.aswin.boxlore.core.playback.setSleepTimer

@Composable
internal fun BoxScope.AppPlaybackNotices(
    repairVisible: Boolean,
    nudgeVisible: Boolean,
    playbackRepository: PlaybackRepository,
    watchTakeover: WatchTakeover = WatchTakeover(),
) {
    AppPlaybackNotices(
        repairVisible = repairVisible,
        nudgeVisible = nudgeVisible,
        sleepActions = LateNightNudgeActions(
            setSleepTimer = { minutes -> playbackRepository.setSleepTimer(minutes, dismissNudge = false) },
            dismissNudge = { playbackRepository.dismissLateNightNudge() },
        ),
        watchTakeover = watchTakeover,
    )
}

/** What the late-night prompt does to playback: set the sleep timer, and put the prompt away. */
internal class LateNightNudgeActions(val setSleepTimer: (minutes: Int) -> Unit, val dismissNudge: () -> Unit)

/**
 * The repair pill, the late-night sleep prompt and the watch notice. On a half-open fold they sit
 * in one half, starting just below the top of that half (below the status bar only when the half
 * reaches the top of the screen).
 */
@Composable
internal fun AppPlaybackNotices(
    repairVisible: Boolean,
    nudgeVisible: Boolean,
    sleepActions: LateNightNudgeActions,
    watchTakeover: WatchTakeover = WatchTakeover(),
) {
    // Above the player sheet and the rest of the root, as each notice was before; the layer itself takes no touches.
    Box(Modifier.fillMaxSize().zIndex(11f).boxloreFoldSafeViewport()) {
        val placement = Modifier
            .align(Alignment.TopCenter)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            .padding(top = 8.dp, start = 16.dp, end = 16.dp)
        RepairProgressPopup(
            visible = repairVisible,
            modifier = placement.zIndex(11f),
        )
        SleepTimerPopup(
            visible = nudgeVisible,
            // Late at night and just taken over from the watch: one card asks both.
            onPlayOnWatch = watchTakeover.onPlayOnWatch.takeIf { watchTakeover.offered },
            modifier = placement.zIndex(10f).testTag(SLEEP_PROMPT_TAG),
            onSelectDuration = { minutes ->
                AnalyticsHelper.trackLateNightSafeguardDecision("timer_set", minutes)
                sleepActions.setSleepTimer(minutes)
            },
            onDismiss = { reason ->
                when (reason) {
                    SleepTimerPopupDismissReason.Manual ->
                        AnalyticsHelper.trackLateNightSafeguardDecision("dismiss")
                    SleepTimerPopupDismissReason.Timeout ->
                        AnalyticsHelper.trackLateNightSafeguardDecision("ignore")
                    SleepTimerPopupDismissReason.Confirmation -> Unit
                    SleepTimerPopupDismissReason.PlayedOnWatch -> Unit
                }
                // The card carried the watch offer too, so that question is answered with it.
                if (watchTakeover.offered) watchTakeover.onDismiss()
                sleepActions.dismissNudge()
            },
        )
        PlaybackPlaceNotice(
            visible = WatchTakeover.showsAlone(watchTakeover.offered, nudgeVisible),
            onPlayOnWatch = watchTakeover.onPlayOnWatch,
            onDismiss = watchTakeover.onDismiss,
            modifier = placement.zIndex(10f),
        )
    }
}

/**
 * This phone has just started playing while the watch was, and the watch was paused for it: the
 * listener is [offered] the watch back until they answer or the notice leaves.
 */
internal class WatchTakeover(val offered: Boolean = false, val onPlayOnWatch: () -> Unit = {}, val onDismiss: () -> Unit = {}) {
    companion object {
        /** The offer has its own card only when the sleep timer prompt is not up to carry it. */
        fun showsAlone(offered: Boolean, sleepPromptVisible: Boolean): Boolean = offered && !sleepPromptVisible
    }
}

internal const val SLEEP_PROMPT_TAG = "sleep_prompt"
