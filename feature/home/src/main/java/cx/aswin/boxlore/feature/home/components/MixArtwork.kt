package cx.aswin.boxlore.feature.home.components

/** Feed artwork may live on either the episode or its parent show. */
internal fun resolveMixArtwork(
    episodeImage: String?,
    episodeShowImage: String?,
    showImage: String?,
    fallbackImage: String?,
): String? = listOf(episodeImage, episodeShowImage, showImage, fallbackImage)
    .firstOrNull { !it.isNullOrBlank() }
    ?.trim()
