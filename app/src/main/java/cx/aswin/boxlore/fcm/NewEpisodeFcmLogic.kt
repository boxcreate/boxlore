package cx.aswin.boxlore.fcm

import android.net.Uri

/** Deep-link and id helpers for `type=new_episode` FCM payloads. */
internal object NewEpisodeFcmLogic {
    fun usableEpisodeId(raw: String?): String? {
        val id = raw?.trim().orEmpty()
        if (id.isEmpty() || id == "0") return null
        return id
    }

    fun route(
        podcastId: String,
        episodeId: String?,
        podcastTitle: String,
    ): String {
        val ep = usableEpisodeId(episodeId)
        return if (ep != null) {
            "boxlore://episode/$ep?autoplay=false&podcastId=${Uri.encode(podcastId)}" +
                "&podcastTitle=${Uri.encode(podcastTitle)}"
        } else {
            "boxlore://podcast/$podcastId"
        }
    }

    fun durationMinutes(
        localDurationSeconds: Int?,
        payloadDurationMinutes: String?,
    ): Int {
        if (localDurationSeconds != null && localDurationSeconds > 0) {
            return localDurationSeconds / 60
        }
        return payloadDurationMinutes?.toIntOrNull()?.coerceAtLeast(0) ?: 0
    }
}
