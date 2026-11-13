package cx.aswin.boxlore.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.components.PlaybackPlaceNotice
import cx.aswin.boxlore.core.designsystem.components.RepairProgressPopup
import cx.aswin.boxlore.core.designsystem.components.SleepTimerPopup
import cx.aswin.boxlore.core.designsystem.components.SleepTimerPopupDismissReason
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.dismissLateNightNudge
import cx.aswin.boxlore.core.playback.setSleepTimer

@Composable
internal fun androidx.compose.foundation.layout.BoxScope.AppPlaybackNotices(
    repairVisible: Boolean,
    nudgeVisible: Boolean,
    playbackRepository: PlaybackRepository,
    watchTakeover: WatchTakeover = WatchTakeover(),
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    RepairProgressPopup(
        visible = repairVisible,
        modifier =
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusBarHeight + 8.dp, start = 16.dp, end = 16.dp)
            .zIndex(11f),
    )
    SleepTimerPopup(
        visible = nudgeVisible,
        // Late at night and just taken over from the watch: one card asks both.
        onPlayOnWatch = watchTakeover.onPlayOnWatch.takeIf { watchTakeover.offered },
        modifier =
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusBarHeight + 8.dp, start = 16.dp, end = 16.dp)
            .zIndex(10f),
        onSelectDuration = { minutes ->
            AnalyticsHelper.trackLateNightSafeguardDecision("timer_set", minutes)
            playbackRepository.setSleepTimer(minutes, dismissNudge = false)
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
            playbackRepository.dismissLateNightNudge()
        },
    )
    PlaybackPlaceNotice(
        visible = WatchTakeover.showsAlone(watchTakeover.offered, nudgeVisible),
        onPlayOnWatch = watchTakeover.onPlayOnWatch,
        onDismiss = watchTakeover.onDismiss,
        modifier =
        Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusBarHeight + 8.dp, start = 16.dp, end = 16.dp)
            .zIndex(10f),
    )
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
