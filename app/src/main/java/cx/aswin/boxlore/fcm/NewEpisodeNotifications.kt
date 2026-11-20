package cx.aswin.boxlore.fcm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import cx.aswin.boxlore.R

internal object NewEpisodeNotifications {
    private const val RELEASE_KEY = "boxlore_release_key"
    private const val SLOT_KEY = "boxlore_episode_slot"
    private const val ORDER_KEY = "boxlore_episode_order"
    private val updateLock = Any()

    /** Posts and cancels Android may not list yet; read and written only under [updateLock]. */
    private val recentPosts = RecentEpisodePosts()

    /** Time for [recentPosts], which must not jump with the wall clock. */
    @androidx.annotation.VisibleForTesting
    internal var clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }

    @androidx.annotation.VisibleForTesting
    internal fun clearRecentPosts() = synchronized(updateLock) { recentPosts.clear() }

    data class NewEpisodeDetails(
        val episodeId: String?,
        val podcastTitle: String,
        val episodeTitle: String,
        val imageUrl: String?,
        val durationMinutes: Int,
        val route: String,
        val artworkSources: List<String> = emptyList(),
    )

    fun resolveNewEpisodeDetails(
        context: Context,
        podcastId: String,
        data: Map<String, String>,
        local: cx.aswin.boxlore.core.model.Episode?,
    ): NewEpisodeDetails {
        // The phone's own row first; else the push's recipe ID, the ID this build stores the episode
        // under, which the episode page resolves through the show the route carries; the push's
        // served ID only when it has no recipe ID (P4 PR 13).
        val episodeId =
            NewEpisodeFcmLogic.usableEpisodeId(local?.id)
                ?: FcmPayloadParser.recipeId(data)?.toString()
                ?: NewEpisodeFcmLogic.usableEpisodeId(FcmPayloadParser.episodeId(data))
        val podcastTitle =
            data["podcastTitle"]?.takeIf { it.isNotBlank() }
                ?: data["podcast_title"]?.takeIf { it.isNotBlank() }
                ?: local?.podcastTitle?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.app_new_episode_show_fallback)
        val episodeTitle =
            local?.title?.takeIf { it.isNotBlank() }
                ?: data["episodeTitle"]?.takeIf { it.isNotBlank() }
                ?: data["episode_title"]?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.app_new_episode_episode_fallback)
        val imageUrl = local?.imageUrl ?: data["image"] ?: data["imageUrl"]
        val duration =
            NewEpisodeFcmLogic.durationMinutes(local?.duration, data["duration"])
        val route = NewEpisodeFcmLogic.route(podcastId, episodeId, podcastTitle)
        return NewEpisodeDetails(
            episodeId = episodeId,
            podcastTitle = podcastTitle,
            episodeTitle = episodeTitle,
            imageUrl = imageUrl,
            artworkSources = cx.aswin.boxlore.core.model.artworkUrls(local?.imageUrl, data["image"], data["imageUrl"], local?.podcastImageUrl, data["feedImage"], data["podcastImageUrl"]),
            durationMinutes = duration,
            route = route,
        )
    }

    private fun showNewEpisodeNotification(context: Context, podcastId: String, details: NewEpisodeDetails, fetchArtwork: Boolean, releaseKey: String, bitmap: android.graphics.Bitmap?) {
        val episodeId = details.episodeId
        val podcastTitle = details.podcastTitle
        val episodeTitle = details.episodeTitle
        val durationMinutes = details.durationMinutes
        val route = details.route
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "boxlore_new_episodes_v1"
        val soundUri = Uri.parse("android.resource://${context.packageName}/raw/boxlore_chime")

        createChannel(context, notificationManager, channelId, soundUri)

        // One notification per show, tagged with its ID; its tap slot is chosen from what is showing,
        // counting this process's own posts and cancels Android does not list yet.
        val active = notificationManager.activeNotifications
        val now = clock()
        val posted = recentPosts.merge(postedEpisodeNotifications(active), now)
        val placement = NewEpisodeFcmLogic.placeEpisodeNotification(podcastId, posted, isUpdate = fetchArtwork)
        val otherShows = posted.count { it.podcastId != podcastId && it.podcastId != placement.replacesPodcastId }
        val summaryShowing = recentPosts.summaryShowing(
            active.any { it.id == NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID && it.tag == null },
            now,
        )
        val summaryPlan = NewEpisodeFcmLogic.planSummary(otherShows, summaryShowing, isUpdate = fetchArtwork)
        // Cancelling the summary takes its whole group with it, so it only ever goes before the post.
        if (summaryPlan.cancelSummaryFirst) {
            notificationManager.cancel(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID)
            recentPosts.recordSummary(showing = false, now)
        }
        placement.replacesPodcastId?.let {
            notificationManager.cancel(it, NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE)
            recentPosts.recordCancel(it, now)
        }
        val requestCode = NewEpisodeFcmLogic.EPISODE_REQUEST_CODE_BASE + placement.slot

        val intent =
            NewEpisodeFcmLogic.createNormalizedPushIntent(
                context = context,
                targetRoute = route,
                notificationType = "new_episode",
                podcastId = podcastId,
                episodeId = episodeId,
            ).setIdentifier(NewEpisodeFcmLogic.EPISODE_INTENT_IDENTIFIER_PREFIX + podcastId)

        val pendingIntent =
            try {
                PendingIntent.getActivity(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } catch (e: SecurityException) {
                android.util.Log.w(
                    "BoxLoreFcmService",
                    "Failed to create PendingIntent for episode notification due to UID quota exhaustion",
                    e,
                )
                null
            }

        val title = context.getString(R.string.app_new_episode_notification_title, podcastTitle)
        val bodyText =
            if (durationMinutes > 0) {
                context.resources.getQuantityString(R.plurals.app_new_episode_notification_body, durationMinutes, episodeTitle, durationMinutes)
            } else {
                context.getString(R.string.app_new_episode_notification_body_no_duration, episodeTitle)
            }

        val notificationBuilder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(cx.aswin.boxlore.R.drawable.ic_notification_custom)
                .setColor(android.graphics.Color.parseColor("#5B5BD6"))
                .setContentTitle(title)
                .setContentText(bodyText)
                .setAutoCancel(true)
                .setOnlyAlertOnce(fetchArtwork)
                .addExtras(
                    android.os.Bundle().apply {
                        putString(RELEASE_KEY, releaseKey)
                        putInt(SLOT_KEY, placement.slot)
                        putLong(ORDER_KEY, placement.order)
                    },
                )
                .setGroup(NewEpisodeFcmLogic.EPISODE_GROUP_KEY)
                .setSound(soundUri)

        if (pendingIntent != null) {
            notificationBuilder.setContentIntent(pendingIntent)
        }

        if (bitmap != null) {
                notificationBuilder.setStyle(
                    NotificationCompat.BigPictureStyle().bigPicture(bitmap)
                    .bigLargeIcon(null as android.graphics.Bitmap?)
                ).setLargeIcon(bitmap)
        }

        notificationManager.notify(podcastId, NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE, notificationBuilder.build())
        recentPosts.recordPost(NewEpisodeFcmLogic.PostedEpisodeNotification(podcastId, placement.slot, placement.order, releaseKey), now)
        // An older build's untagged copy of this show would now show twice; it was never grouped.
        NewEpisodeFcmLogic.legacyCopyToClear(podcastId, NewEpisodeFcmLogic.legacyEpisodeNotificationTitle(podcastTitle), legacyEpisodeNotifications(active))?.let { notificationManager.cancel(it) }
        summaryPlan.summaryCount?.let {
            postSummary(context, notificationManager, channelId, it)
            recentPosts.recordSummary(showing = true, now)
        }
    }

    private fun createChannel(context: Context, notificationManager: NotificationManager, channelId: String, soundUri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val audioAttributes =
            AudioAttributes
                .Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()
        val channel =
            NotificationChannel(
                channelId,
                context.getString(R.string.app_channel_new_episodes),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.app_channel_new_episodes_description)
                setSound(soundUri, audioAttributes)
            }
        notificationManager.createNotificationChannel(channel)
    }

    /** Untagged notifications older builds left in the new-episode id range. */
    private fun legacyEpisodeNotifications(active: Array<StatusBarNotification>): List<NewEpisodeFcmLogic.LegacyEpisodeNotification> {
        val ids = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE until
            NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE + NewEpisodeFcmLogic.MAX_EPISODE_NOTIFICATION_SLOTS
        return active
            .filter { it.tag == null && it.id in ids }
            .map { NewEpisodeFcmLogic.LegacyEpisodeNotification(it.id, it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()) }
    }

    /** The new-episode notifications showing now, read back from the tag, slot and order each one carries. */
    private fun postedEpisodeNotifications(active: Array<StatusBarNotification>): List<NewEpisodeFcmLogic.PostedEpisodeNotification> =
        active.mapNotNull { posted ->
            val tag = posted.tag
            val extras = posted.notification.extras
            if (posted.id != NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE || tag.isNullOrEmpty() || !extras.containsKey(SLOT_KEY)) {
                null
            } else {
                NewEpisodeFcmLogic.PostedEpisodeNotification(
                    tag,
                    extras.getInt(SLOT_KEY),
                    extras.getLong(ORDER_KEY, posted.postTime),
                    extras.getString(RELEASE_KEY),
                )
            }
        }

    /** Bundles two or more shows' notifications under one quiet summary. */
    private fun postSummary(context: Context, manager: NotificationManager, channelId: String, showing: Int) {
        val intent = NewEpisodeFcmLogic.createNormalizedPushIntent(
            context,
            targetRoute = NewEpisodeFcmLogic.NEW_EPISODES_ROUTE,
            notificationType = NewEpisodeFcmLogic.SUMMARY_NOTIFICATION_TYPE,
        )
        val pendingIntent =
            try {
                PendingIntent.getActivity(
                    context,
                    NewEpisodeFcmLogic.EPISODE_SUMMARY_REQUEST_CODE,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } catch (e: SecurityException) {
                android.util.Log.w("BoxLoreFcmService", "Failed to create PendingIntent for new episodes summary", e)
                null
            }
        val summary =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(cx.aswin.boxlore.R.drawable.ic_notification_custom)
                .setColor(android.graphics.Color.parseColor("#5B5BD6"))
                .setContentTitle(context.getString(R.string.app_new_episodes_summary_title))
                .setContentText(context.resources.getQuantityString(R.plurals.app_new_episodes_summary_text, showing, showing))
                .setGroup(NewEpisodeFcmLogic.EPISODE_GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setAutoCancel(true)
        if (pendingIntent != null) summary.setContentIntent(pendingIntent)
        manager.notify(NewEpisodeFcmLogic.EPISODE_SUMMARY_NOTIFICATION_ID, summary.build())
    }

    fun show(context: Context, podcastId: String, data: Map<String, String>, local: cx.aswin.boxlore.core.model.Episode? = null, fetchArtwork: Boolean = false, canPresent: () -> Boolean = { true }) {
        val releaseKey = NewEpisodeDeliveryWorker.workName(data + ("podcastId" to podcastId))
        if (fetchArtwork && !isCurrentRelease(context, podcastId, releaseKey)) return
        val details = resolveNewEpisodeDetails(context, podcastId, data, local)
        val bitmap = if (fetchArtwork) {
            kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.withTimeoutOrNull(6_000) {
                cx.aswin.boxlore.core.designsystem.components.loadArtworkBitmap(context, details.artworkSources, perAttemptTimeoutMillis = 700)
            }
        }
        } else {
            null
        }
        synchronized(updateLock) {
            // Recheck after artwork I/O, before updating the show's notification and its tap slot.
            if (fetchArtwork && !isCurrentRelease(context, podcastId, releaseKey)) return
            if (!canPresent()) return
            showNewEpisodeNotification(context, podcastId, details, fetchArtwork, releaseKey, bitmap)
        }
    }

    private fun isCurrentRelease(context: Context, podcastId: String, releaseKey: String): Boolean {
        synchronized(updateLock) {
            // A newer release or a cancel this process just made may not be listed yet.
            if (recentPosts.supersedes(podcastId, releaseKey, clock())) return false
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return manager.activeNotifications.any {
            it.id == NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE &&
                it.tag == podcastId &&
                it.notification.extras.getString(RELEASE_KEY) == releaseKey
        }
    }
}
