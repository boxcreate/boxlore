package cx.aswin.boxlore.core.rss

/** Artwork for feed-only extras: item → channel → show (PI) image. */
object EpisodeSupplementArtworkLogic {
    fun resolvedImageUrl(itemImageUrl: String?, channelImageUrl: String? = null, showImageUrl: String? = null,): String? = itemImageUrl?.takeIf { it.isNotBlank() }
        ?: channelImageUrl?.takeIf { it.isNotBlank() }
        ?: showImageUrl?.takeIf { it.isNotBlank() }
}
