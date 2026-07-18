package cx.aswin.boxlore.core.data.ports

/**
 * Port that lets [cx.aswin.boxlore.core.data.RssPodcastRepository] re-key a Media3 download
 * cache entry when an RSS episode ID changes, without creating a compile-time dependency on
 * `:core:downloads` from `:core:data`.
 *
 * The implementation ([cx.aswin.boxlore.core.data.DownloadRepository.relinkDownloadCache])
 * lives in `:core:downloads` and is wired by [AppContainer] at startup.
 *
 * A no-op default is used before the container installs the real implementation.
 */
fun interface DownloadCacheRelinker {
    fun relink(oldEpisodeId: String, newEpisodeId: String)
}
