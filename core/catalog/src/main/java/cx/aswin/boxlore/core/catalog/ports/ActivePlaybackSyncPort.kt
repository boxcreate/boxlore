package cx.aswin.boxlore.core.catalog.ports

/**
 * Port decoupling active playback state checks and idle session handoff in the catalog engine
 * from the playback module.
 */
interface ActivePlaybackSyncPort {
    fun getActivePlayingEpisodeId(): String?
    fun updateIdlePlaybackSession(episodeId: String, positionMs: Long, lastPlayedAt: Long) {}
}
