package cx.aswin.boxlore.wear.glance

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import cx.aswin.boxlore.wear.MainActivity
import cx.aswin.boxlore.wear.R
import cx.aswin.boxlore.wear.WatchApplication
import cx.aswin.boxlore.wear.ui.WatchRoutes

/**
 * The watch-face shortcut: the episode to jump back into as a line of text, a short "26m", a
 * progress ring or the boxlore mark alone, whichever the watch face has room for. A tap resumes
 * the episode. With nothing in progress it is the mark, and a tap opens the app.
 */
class BoxloreComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, PREVIEW)

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? =
        data(request.complicationType, (application as? WatchApplication)?.container?.glance?.invoke())

    private fun data(type: ComplicationType, glance: WatchGlance?): ComplicationData? {
        val name = getString(R.string.wear_app_name)
        val timeLeft = glance?.let { getString(R.string.wear_minutes_left, it.minutesLeft) }
        val long = if (glance == null || timeLeft == null) name else getString(R.string.wear_home_state_and_time, glance.title, timeLeft)
        val spoken = plain(if (glance == null) name else getString(R.string.wear_complication_description, glance.title, timeLeft))
        val mark = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_boxlore_mark)).build()
        val tap = tap(this, resume = glance?.episodeId?.takeIf { it.isNotEmpty() })
        return when (type) {
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(plain(long), spoken).setMonochromaticImage(mark).setTapAction(tap).build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(plain(glance?.let { GlanceRules.shortTime(it.minutesLeft) } ?: name), spoken)
                    .setMonochromaticImage(mark)
                    .setTapAction(tap)
                    .build()
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(value = glance?.progress ?: 0f, min = 0f, max = 1f, contentDescription = spoken)
                    .setMonochromaticImage(mark)
                    .setTapAction(tap)
                    .build()
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(mark, spoken).setTapAction(tap).build()
            else -> null
        }
    }

    private fun plain(text: String): ComplicationText = PlainComplicationText.Builder(text).build()

    companion object {
        /** What the watch-face picker shows before the listener has chosen the shortcut. */
        private val PREVIEW = WatchGlance(episodeId = "", title = "Blackshades", showTitle = "Darknet Diaries", minutesLeft = 26, progress = 0.36f)

        /** Redraws the shortcut on every watch face that uses it. */
        fun requestUpdate(context: Context) {
            ComplicationDataSourceUpdateRequester.create(context, ComponentName(context, BoxloreComplicationService::class.java)).requestUpdateAll()
        }

        private fun tap(context: Context, resume: String?): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (resume != null) intent.putExtra(WatchRoutes.EXTRA_RESUME, resume)
            // One request code per kind of tap, so resuming and plain opening never share an intent.
            return PendingIntent.getActivity(context, if (resume != null) 1 else 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
