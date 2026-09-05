package cx.aswin.boxlore.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import cx.aswin.boxlore.BoxLoreApplication
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.designsystem.components.optimizedImageUrl
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.ui.announcement.shouldSuppressWhatsNewOnPlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BoxLoreFcmService : FirebaseMessagingService() {
    private val CHANNEL_ID = "boxlore_announcements_v2"

    private fun userPreferences(): UserPreferencesRepository = (application as? BoxLoreApplication)?.userPreferencesRepository
        ?: SharedAppDependenciesHolder.require().userPreferencesRepository

    // Firebase Messaging still delivers rotation callbacks here; TokenWatcher migration is follow-up.
    @Suppress("DEPRECATION")
    @Deprecated("Overrides deprecated FirebaseMessagingService.onNewToken")
    override fun onNewToken(token: String) {
        @Suppress("DEPRECATION")
        super.onNewToken(token)
        // Subscribe to the global announcements topic
        FirebaseMessaging.getInstance().subscribeToTopic("all_users")

        // Subscribe to environment-specific topic safely by clearing the antagonist topic
        if (cx.aswin.boxlore.BuildConfig.DEBUG) {
            FirebaseMessaging.getInstance().subscribeToTopic("debug_users")
            FirebaseMessaging.getInstance().unsubscribeFromTopic("prod_users")
        } else {
            FirebaseMessaging.getInstance().subscribeToTopic("prod_users")
            FirebaseMessaging.getInstance().unsubscribeFromTopic("debug_users")
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        if (data.isEmpty()) return

        val type = data["type"] ?: "push"
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackNotificationReceived(
            notificationType = type,
            podcastId = FcmPayloadParser.podcastId(data),
            episodeId = FcmPayloadParser.episodeId(data),
        )
        if (type == "new_episode") {
            handleNewEpisodeMessage(data)
            return
        }

        val parsed = FcmPayloadParser.parse(data)

        if (parsed.type == "in-app" || parsed.type == "both") {
            saveInAppAnnouncement(
                parsed.title,
                parsed.body,
                parsed.route,
                parsed.imageUrl,
                parsed.actionLabel,
                parsed.showActionInApp,
                parsed.category,
            )
        }

        if (parsed.type == "push" || parsed.type == "both") {
            handlePushAnnouncement(parsed, type)
        }
    }

    private fun handlePushAnnouncement(parsed: ParsedFcmNotification, type: String) {
        if (applicationContext.shouldSuppressWhatsNewOnPlay(parsed.category)) {
            android.util.Log.d(
                "BoxLoreFcmService",
                "Skipping Whats New push on Play Store install (category=${parsed.category})",
            )
            return
        }
        try {
            showPushNotification(parsed.copy(type = type))
        } catch (e: Exception) {
            android.util.Log.e("BoxLoreFcmService", "Failed to show push notification", e)
        }
    }

    private data class NewEpisodeDetails(
        val episodeId: String?,
        val podcastTitle: String,
        val episodeTitle: String,
        val imageUrl: String?,
        val durationMinutes: Int,
        val route: String,
    )

    private fun resolveNewEpisodeDetails(
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

    private fun handleNewEpisodeMessage(data: Map<String, String>) {
        val podcastId = FcmPayloadParser.podcastId(data) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val deps = SharedAppDependenciesHolder.instance
            val local =
                if (deps != null) {
                    try {
                        NewEpisodePushHydration.resolveLocalEpisode(
                            podcastId = podcastId,
                            payloadFeedUrl = FcmPayloadParser.feedUrl(data),
                            payloadEnclosureUrl = FcmPayloadParser.enclosureUrl(data),
                            payloadGuid = FcmPayloadParser.guid(data),
                            sources =
                            NewEpisodePushHydration.Sources(
                                subscriptionRepository = deps.subscriptionRepository,
                                episodeSupplementPort = deps.podcastRepository.episodeSupplementRepository,
                                localEpisodeCatalog = deps.podcastRepository.localEpisodeCatalog,
                                loadPiBaseline =
                                NewEpisodePushHydration.piBaselineLoader { feedId, limit ->
                                    deps.podcastRepository.loadPiEpisodesForBaseline(feedId, limit)
                                },
                            ),
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                } else {
                    null
                }
            val details = resolveNewEpisodeDetails(podcastId, data, local)
            NewEpisodeFcmLogic.executeEpisodeDelivery(
                triggerAutoDownload = {
                    if (details.episodeId != null) {
                        triggerAutoDownload(podcastId, details.episodeId)
                    }
                },
                showNotification = {
                    showNewEpisodeNotification(
                        podcastId = podcastId,
                        episodeId = details.episodeId,
                        podcastTitle = details.podcastTitle,
                        episodeTitle = details.episodeTitle,
                        imageUrl = details.imageUrl,
                        durationMinutes = details.durationMinutes,
                        route = details.route,
                    )
                },
            )
        }
    }

    private fun showNewEpisodeNotification(
        podcastId: String,
        episodeId: String?,
        podcastTitle: String,
        episodeTitle: String,
        imageUrl: String?,
        durationMinutes: Int,
        route: String,
    ) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "boxlore_new_episodes_v1"
        val soundUri = Uri.parse("android.resource://$packageName/raw/boxlore_chime")

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
                context = this,
                targetRoute = route,
                notificationType = "new_episode",
                podcastId = podcastId,
                episodeId = episodeId,
            )

        val pendingIntent =
            try {
                PendingIntent.getActivity(
                    this,
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
                .Builder(this, channelId)
                .setSmallIcon(cx.aswin.boxlore.R.drawable.ic_notification_custom)
                .setColor(android.graphics.Color.parseColor("#5B5BD6"))
                .setContentTitle("New Episode • $podcastTitle")
                .setContentText(bodyText)
                .setAutoCancel(true)
                .setSound(soundUri)

        if (pendingIntent != null) {
            notificationBuilder.setContentIntent(pendingIntent)
        }

        if (!imageUrl.isNullOrBlank()) {
            try {
                val optimizedUrl = imageUrl.optimizedImageUrl(500)
                val url = java.net.URL(optimizedUrl)
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.doInput = true
                connection.connect()
                val bitmap = android.graphics.BitmapFactory.decodeStream(connection.inputStream)
                if (bitmap != null) {
                    notificationBuilder.setStyle(
                        NotificationCompat
                            .BigPictureStyle()
                            .bigPicture(bitmap)
                            .bigLargeIcon(null as android.graphics.Bitmap?),
                    )
                    notificationBuilder.setLargeIcon(bitmap)
                }
            } catch (e: Exception) {
                // Ignore image fetch failure
            }
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private suspend fun triggerAutoDownload(podcastId: String, episodeId: String) {
        try {
            android.util.Log.i(
                "BoxLore_BackgroundTrace",
                "[FCM] Received new_episode trigger for podcastId: $podcastId, episodeId: $episodeId",
            )

            val userPrefs = userPreferences()
            val wifiOnly = userPrefs.autoDownloadWifiOnlyStream.first()

            android.util.Log.i(
                "BoxLore_BackgroundTrace",
                "[FCM] Preparing AutoDownloadWorker. wifiOnly=$wifiOnly",
            )

            NewEpisodeFcmLogic.enqueueAutoDownload(
                workManager = androidx.work.WorkManager.getInstance(applicationContext),
                podcastId = podcastId,
                episodeId = episodeId,
                wifiOnly = wifiOnly,
            )
            android.util.Log.i(
                "BoxLore_BackgroundTrace",
                "[FCM] Successfully enqueued AutoDownloadWorker into WorkManager for podcast $podcastId",
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("BoxLore_BackgroundTrace", "[FCM] Error enqueuing AutoDownloadWorker", e)
        }
    }

    private fun saveInAppAnnouncement(
        title: String,
        body: String,
        route: String?,
        imageUrl: String?,
        actionLabel: String?,
        showActionInApp: Boolean,
        category: String,
    ) {
        // GitHub APK "What's New" / release download CTA is meaningless on Play installs.
        if (applicationContext.shouldSuppressWhatsNewOnPlay(category)) {
            android.util.Log.d(
                "BoxLoreFcmService",
                "Skipping Whats New in-app announcement on Play Store install (category=$category)",
            )
            return
        }

        val prefs = userPreferences()
        CoroutineScope(Dispatchers.IO).launch {
            val announcement =
                UserPreferencesRepository.Announcement(
                    title = title,
                    body = body,
                    route = route,
                    imageUrl = imageUrl,
                    actionLabel = actionLabel,
                    showActionInApp = showActionInApp,
                    category = category,
                    timestamp = System.currentTimeMillis(),
                )
            prefs.setAnnouncement(announcement)
        }
    }

    private fun showPushNotification(notification: ParsedFcmNotification) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val config = getPushChannelConfig(notification.sound)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(config.id, config.name, config.importance).apply {
                    description = "boxlore news and updates"
                    if (config.soundUri != null) {
                        val audioAttributes =
                            AudioAttributes
                                .Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .build()
                        setSound(config.soundUri, audioAttributes)
                    } else {
                        setSound(null, null)
                    }
                }
            notificationManager.createNotificationChannel(channel)
        }

        val announcementKey = notification.podcastId ?: notification.route ?: notification.title
        val slot = NewEpisodeFcmLogic.announcementSlot(announcementKey)
        val notificationId = NewEpisodeFcmLogic.ANNOUNCEMENT_NOTIFICATION_ID_BASE + slot
        val contentRequestCode = NewEpisodeFcmLogic.ANNOUNCEMENT_REQUEST_CODE_BASE + slot
        val actionRequestCode = NewEpisodeFcmLogic.ANNOUNCEMENT_ACTION_REQUEST_CODE_BASE + slot

        val intent =
            createPushIntent(
                notification.route,
                notification.type,
                notification.podcastId,
                notification.episodeId,
            )
        val pendingIntent =
            try {
                PendingIntent.getActivity(
                    this,
                    contentRequestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            } catch (e: SecurityException) {
                android.util.Log.w(
                    "BoxLoreFcmService",
                    "Failed to create PendingIntent for push announcement due to UID quota exhaustion",
                    e,
                )
                null
            }

        val notificationBuilder =
            NotificationCompat
                .Builder(this, config.id)
                .setSmallIcon(cx.aswin.boxlore.R.drawable.ic_notification_custom)
                .setColor(android.graphics.Color.parseColor("#5B5BD6")) // Brand purple color matching launcher icon
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setAutoCancel(true)

        if (pendingIntent != null) {
            notificationBuilder.setContentIntent(pendingIntent)
        }

        if (config.soundUri != null) {
            notificationBuilder.setSound(config.soundUri)
        }

        val route = notification.route
        if (notification.showActionInPush && !route.isNullOrBlank()) {
            val actionIntent =
                createPushIntent(
                    route,
                    notification.type,
                    notification.podcastId,
                    notification.episodeId,
                )
            val actionPendingIntent =
                try {
                    PendingIntent.getActivity(
                        this,
                        actionRequestCode,
                        actionIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                } catch (e: SecurityException) {
                    android.util.Log.w(
                        "BoxLoreFcmService",
                        "Failed to create action PendingIntent for push announcement due to UID quota exhaustion",
                        e,
                    )
                    null
                }
            if (actionPendingIntent != null) {
                notificationBuilder.addAction(
                    cx.aswin.boxlore.R.drawable.ic_notification_custom,
                    notification.actionLabel,
                    actionPendingIntent,
                )
            }
        }

        loadPushImage(notificationBuilder, notification.imageUrl)
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    private data class PushChannelConfig(val id: String, val name: String, val soundUri: Uri?, val importance: Int,)

    private fun getPushChannelConfig(sound: String?): PushChannelConfig = when (sound) {
        "chime" ->
            PushChannelConfig(
                id = "boxlore_new_episodes_v1",
                name = "New Episode Alerts",
                soundUri = Uri.parse("android.resource://$packageName/raw/boxlore_chime"),
                importance = NotificationManager.IMPORTANCE_DEFAULT,
            )
        "silent" ->
            PushChannelConfig(
                id = "boxlore_silent_v1",
                name = "Silent Notifications",
                soundUri = null,
                importance = NotificationManager.IMPORTANCE_LOW,
            )
        else ->
            PushChannelConfig(
                id = "boxlore_announcements_v2",
                name = "Announcements",
                soundUri = Uri.parse("android.resource://$packageName/raw/boxlore_announcement_chime"),
                importance = NotificationManager.IMPORTANCE_DEFAULT,
            )
    }

    private fun createPushIntent(
        route: String?,
        notificationType: String = "push",
        podcastId: String? = null,
        episodeId: String? = null,
    ): Intent =
        NewEpisodeFcmLogic.createNormalizedPushIntent(
            context = this,
            targetRoute = route,
            notificationType = notificationType,
            podcastId = podcastId,
            episodeId = episodeId,
        )

    private fun loadPushImage(builder: NotificationCompat.Builder, imageUrl: String?,) {
        if (imageUrl.isNullOrBlank()) return
        try {
            val optimizedUrl = imageUrl.optimizedImageUrl(500)
            val url = java.net.URL(optimizedUrl)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.doInput = true
            connection.connect()
            val bitmap = android.graphics.BitmapFactory.decodeStream(connection.inputStream)
            if (bitmap != null) {
                builder.setStyle(
                    NotificationCompat
                        .BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as android.graphics.Bitmap?),
                )
                builder.setLargeIcon(bitmap)
            }
        } catch (e: Exception) {
            // Ignore image fetch failure
        }
    }
}
