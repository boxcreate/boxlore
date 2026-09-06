package cx.aswin.boxlore.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SubscriptionFolder(
    val id: String,
    val name: String,
    val icon: String? = null,
    val displaySize: FolderDisplaySize = FolderDisplaySize.COMPACT,
    val linkedGenre: String? = null,
    val showPodcastGrid: Boolean = false,
    val createdAt: Long = 0L,
    val podcastCount: Int = 0,
    val podcastIds: List<String> = emptyList(),
) {
    val hasIcon: Boolean
        get() = !icon.isNullOrBlank()

    val isGenreLinked: Boolean
        get() = !linkedGenre.isNullOrBlank()

    val effectiveShowPodcastGrid: Boolean
        get() = showPodcastGrid || icon.isNullOrBlank()
}
