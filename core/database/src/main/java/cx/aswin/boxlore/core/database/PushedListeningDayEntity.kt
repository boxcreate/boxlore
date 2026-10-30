package cx.aswin.boxlore.core.database

import androidx.room.Entity

/**
 * One day of this phone's listening as the cloud last stored it (Room v40): the cloud sync push sends a
 * day whose current total differs from this, and a zero day for one pushed before that is gone (its
 * listening moved to another episode ID, or was removed), so every phone counts it once.
 */
@Entity(
    tableName = "listening_days_pushed",
    primaryKeys = ["deviceId", "localDay", "episodeId"],
)
data class PushedListeningDayEntity(
    val deviceId: String,
    val localDay: Long,
    val episodeId: String,
    val podcastId: String,
    val consumedMs: Long,
    val wallMs: Long,
    val sessionCount: Int,
    val completionCount: Int,
    val morningMs: Long,
    val afternoonMs: Long,
    val eveningMs: Long,
    val nightMs: Long,
    val lastListenedAt: Long,
) {
    fun toRollup() = ListeningRollupEntity(
        localDay = localDay,
        episodeId = episodeId,
        podcastId = podcastId,
        consumedMs = consumedMs,
        sessionCount = sessionCount,
        completionCount = completionCount,
        lastListenedAt = lastListenedAt,
        morningMs = morningMs,
        afternoonMs = afternoonMs,
        eveningMs = eveningMs,
        nightMs = nightMs,
        deviceId = deviceId,
        wallMs = wallMs,
    )

    companion object {
        fun of(day: ListeningRollupEntity) = PushedListeningDayEntity(
            deviceId = day.deviceId,
            localDay = day.localDay,
            episodeId = day.episodeId,
            podcastId = day.podcastId,
            consumedMs = day.consumedMs,
            wallMs = day.wallMs,
            sessionCount = day.sessionCount,
            completionCount = day.completionCount,
            morningMs = day.morningMs,
            afternoonMs = day.afternoonMs,
            eveningMs = day.eveningMs,
            nightMs = day.nightMs,
            lastListenedAt = day.lastListenedAt,
        )
    }
}
