package cx.aswin.boxlore.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import cx.aswin.boxlore.core.designsystem.components.optimizedImageUrl

internal object NewEpisodeNotifications {
    data class NewEpisodeDetails(
        val episodeId: String?,
        val podcastTitle: String,
        val episodeTitle: String,
        val imageUrl: String?,
        val durationMinutes: Int,
        val route: String,
    )

    fun resolveNewEpisodeDetails(
        podcastId: String,
        data: Map<String, String>,
        local: cx.aswin.boxlore.core.model.Episode?,
    ): NewEpisodeDetails {
        val episodeId =
            NewEpisodeFcmLogic.usableEpisodeId(local?.id)
                ?: NewEpisodeFcmLogic.usableEpisodeId(FcmPayloadParser.episodeId(data))
        val podcastTitle =
            data["podcastTitle"]?.takeIf { it.isNotBlank() }
                ?: data["podcast_title"]?.takeIf { it.isNotBlank() }
                ?: local?.podcastTitle?.takeIf { it.isNotBlank() }
                ?: "New Release"
        val episodeTitle =
            local?.title?.takeIf { it.isNotBlank() }
                ?: data["episodeTitle"]?.takeIf { it.isNotBlank() }
                ?: data["episode_title"]?.takeIf { it.isNotBlank() }
                ?: "New Episode"
        val imageUrl = local?.imageUrl ?: data["image"] ?: data["imageUrl"]
        val duration =
            NewEpisodeFcmLogic.durationMinutes(local?.duration, data["duration"])
        val route = NewEpisodeFcmLogic.route(podcastId, episodeId, podcastTitle)
        return NewEpisodeDetails(
            episodeId = episodeId,
            podcastTitle = podcastTitle,
            episodeTitle = episodeTitle,
            imageUrl = imageUrl,
            durationMinutes = duration,
            route = route,
        )
    }

    private fun showNewEpisodeNotification(context: Context, podcastId: String, details: NewEpisodeDetails, fetchArtwork: Boolean) {
        val episodeId = details.episodeId
        val podcastTitle = details.podcastTitle
        val episodeTitle = details.episodeTitle
        val imageUrl = details.imageUrl
        val durationMinutes = details.durationMinutes
        val route = details.route
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "boxlore_new_episodes_v1"
        val soundUri = Uri.parse("android.resource://${context.packageName}/raw/boxlore_chime")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes =
                AudioAttributes
                    .Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            val channel =
                NotificationChannel(
                    channelId,
                    "New Episodes",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Alerts for new podcast episodes"
                    setSound(soundUri, audioAttributes)
                }
            notificationManager.createNotificationChannel(channel)
        }

        val slot = NewEpisodeFcmLogic.episodeSlot(podcastId)
        val notificationId = NewEpisodeFcmLogic.EPISODE_NOTIFICATION_ID_BASE + slot
        val requestCode = NewEpisodeFcmLogic.EPISODE_REQUEST_CODE_BASE + slot

        val intent =
            NewEpisodeFcmLogic.createNormalizedPushIntent(
                context = context,
                targetRoute = route,
                notificationType = "new_episode",
                podcastId = podcastId,
                episodeId = episodeId,
            )

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

        val bodyText =
            if (durationMinutes > 0) {
                "\"$episodeTitle\" ($durationMinutes mins)"
            } else {
                "\"$episodeTitle\""
            }

        val notificationBuilder =
            NotificationCompat
                .Builder(context, channelId)
                .setSmallIcon(cx.aswin.boxlore.R.drawable.ic_notification_custom)
                .setColor(android.graphics.Color.parseColor("#5B5BD6"))
                .setContentTitle("New Episode • $podcastTitle")
                .setContentText(bodyText)
                .setAutoCancel(true)
                .setOnlyAlertOnce(fetchArtwork)
                .setSound(soundUri)

        if (pendingIntent != null) {
            notificationBuilder.setContentIntent(pendingIntent)
        }

        if (fetchArtwork && !imageUrl.isNullOrBlank()) {
            fetchBitmap(imageUrl)?.let { bitmap ->
                notificationBuilder.setStyle(
                    NotificationCompat.BigPictureStyle().bigPicture(bitmap)
                    .bigLargeIcon(null as android.graphics.Bitmap?)
                ).setLargeIcon(bitmap)
            }
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private fun fetchBitmap(imageUrl: String): android.graphics.Bitmap? = try {
        val connection = java.net.URL(imageUrl.optimizedImageUrl(500)).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 2_000
        connection.readTimeout = 2_000
        try {
            connection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    fun show(context: Context, podcastId: String, data: Map<String, String>, local: cx.aswin.boxlore.core.model.Episode? = null, fetchArtwork: Boolean = false) {
        val details = resolveNewEpisodeDetails(podcastId, data, local)
        showNewEpisodeNotification(context, podcastId, details, fetchArtwork)
    }
}
