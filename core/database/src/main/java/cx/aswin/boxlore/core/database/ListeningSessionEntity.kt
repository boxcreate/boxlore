package cx.aswin.boxlore.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One closed listening session with naturally consumed audio milliseconds.
 * Primary key is a stable UUID so backup restore can upsert without duplicating.
 */
@Entity(
    tableName = "listening_sessions",
    indices = [
        Index(value = ["endedAt"]),
        Index(value = ["episodeId"]),
        Index(value = ["podcastId"]),
        Index(value = ["localDay"]),
    ],
)
data class ListeningSessionEntity(
    @PrimaryKey
    val sessionId: String,
    val episodeId: String,
    val podcastId: String,
    val startedAt: Long,
    val endedAt: Long,
    /** Seek-excluding consumed audio from PlaybackTelemetrySession. */
    val consumedMs: Long,
    val completed: Boolean,
    /** Local calendar day as epoch day (ZoneId.systemDefault). */
    val localDay: Long,
    /** 0=morning, 1=afternoon, 2=evening, 3=night */
    val timeBucket: Int,
    // Room v40; null on sessions recorded before it ([ListeningSessionValues]).
    /** The phone that recorded it: null for this phone, else that phone's sync device ID (a restored backup). */
    val deviceId: String? = null,
    /** Listening time it took: [consumedMs] at the playback speed, for time saved by speed. */
    val wallMs: Long? = null,
    /** How the play started: a canonical entry point, [ListeningSessionValues.SOURCE_LORE] or [ListeningSessionValues.SOURCE_SMART_QUEUE]. */
    val source: String? = null,
    /** Where it was heard ([ListeningSessionValues] `OUTPUT_*`). */
    val output: String? = null,
    /** The episode's release date, epoch ms. */
    val episodePublishedAt: Long? = null,
)

/** [ListeningSessionEntity.deviceId] as a daily summary stores it: this phone's sessions sum into [ListeningRollupEntity.THIS_DEVICE]. */
val ListeningSessionEntity.rollupDeviceId: String get() = deviceId ?: ListeningRollupEntity.THIS_DEVICE
