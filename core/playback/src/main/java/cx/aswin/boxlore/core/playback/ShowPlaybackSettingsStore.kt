package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** A show's own playback settings; a null value uses the app setting. */
data class ShowPlaybackOverrides(
    val podcastId: String,
    val skipBeginningMs: Long? = null,
    val skipEndingMs: Long? = null,
    val speed: Float? = null,
    val trimSilence: Boolean? = null,
)

/** Reads and writes a show's own speed and trim silence for the player and the playback service. */
interface ShowPlaybackSettingsStore {
    /** The show's settings as they change; a show without a stored row has none of its own. */
    fun observe(podcastId: String): Flow<ShowPlaybackOverrides>

    suspend fun setSpeed(podcastId: String, speed: Float?)

    suspend fun setTrimSilence(podcastId: String, enabled: Boolean?)
}

/** [ShowPlaybackSettingsStore] over the `podcasts` table; a write to a show without a row does nothing. */
class RoomShowPlaybackSettingsStore(private val podcastDao: PodcastDao) : ShowPlaybackSettingsStore {
    override fun observe(podcastId: String): Flow<ShowPlaybackOverrides> = podcastDao
        .observePodcast(podcastId)
        .map { row -> row.showPlaybackOverrides(podcastId) }
        .distinctUntilChanged()

    // Cloud sync doesn't carry speed or trim silence, so a player change never queues the row for upload.
    override suspend fun setSpeed(podcastId: String, speed: Float?) = podcastDao.setPlaybackSpeedOverrideLocally(podcastId, speed)

    override suspend fun setTrimSilence(podcastId: String, enabled: Boolean?) = podcastDao.setSkipSilenceOverrideLocally(podcastId, enabled)
}

/** This row's own playback settings, or none for a show without a row. */
internal fun PodcastEntity?.showPlaybackOverrides(podcastId: String): ShowPlaybackOverrides = ShowPlaybackOverrides(
    podcastId = podcastId,
    skipBeginningMs = this?.skipBeginningOverrideMs,
    skipEndingMs = this?.skipEndingOverrideMs,
    speed = this?.playbackSpeedOverride,
    trimSilence = this?.skipSilenceOverride,
)
