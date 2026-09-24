package cx.aswin.boxlore.core.catalog.ports

/**
 * Port decoupling active playback state checks in the catalog engine from the playback module.
 */
fun interface ActivePlaybackSyncPort {
    fun getActivePlayingEpisodeId(): String?
}
