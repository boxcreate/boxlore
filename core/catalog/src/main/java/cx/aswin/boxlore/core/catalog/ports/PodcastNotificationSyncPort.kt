package cx.aswin.boxlore.core.catalog.ports

/**
 * Port decoupling cloud sync subscription notifications and topic bindings
 * from Firebase and messaging clients.
 */
fun interface PodcastNotificationSyncPort {
    suspend fun setNotificationTopicSubscribed(podcastId: String, subscribed: Boolean)
}
