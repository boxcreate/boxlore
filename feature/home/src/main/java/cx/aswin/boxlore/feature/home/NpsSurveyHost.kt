package cx.aswin.boxlore.feature.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.feature.home.components.NpsSurveySheet

/**
 * Shows [controller]'s survey over Home. A decided survey is checked again as it appears: it waits while
 * something plays, is dropped once its finish moment has passed ([nowMs]), and counts as shown only once
 * Home is resumed with the sheet on screen.
 */
@Composable
internal fun NpsSurveyHost(controller: NpsSurveyController, isPlaying: Boolean, nowMs: () -> Long = System::currentTimeMillis) {
    val session by controller.session.collectAsState()
    val current = session ?: return
    if (!controller.wasDisplayed && current.isExpired(nowMs())) {
        LaunchedEffect(current.submissionId) { controller.discardUndisplayed() }
        return
    }
    if (!current.manual && isPlaying && !controller.wasDisplayed) return
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, current.submissionId) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { controller.onDisplayed() }
    }
    val context = LocalContext.current
    NpsSurveySheet(
        session = current,
        copy = controller.copy,
        onScore = controller::onScore,
        onFollowUpDone = controller::onFollowUpDone,
        onClose = controller::onClose,
        onRateOnPlay = {
            AnalyticsHelper.trackStoreReviewOpened(method = "store_page", source = "nps_survey")
            openPlayStorePage(context)
            controller.onClose()
        },
    )
}

/** boxlore's Play Store page: the Play app, or the web page when Play isn't there. */
internal fun openPlayStorePage(context: Context) {
    val pkgName = context.packageName
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkgName")))
    } catch (e: android.content.ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkgName")))
    }
}
