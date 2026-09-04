package cx.aswin.boxlore.core.model

import kotlinx.serialization.Serializable

/** A person associated with a podcast or episode (host, guest, editor, etc.) */
@Serializable
data class Person(
    val name: String,
    // "host", "guest", "editor"
    val role: String? = null,
    // "cast", "writing", "visuals"
    val group: String? = null,
    val img: String? = null,
    val href: String? = null,
)

/** A transcript resource associated with an episode */
@Serializable
data class Transcript(
    val url: String,
    // "application/srt", "text/vtt", "application/json"
    val type: String,
)

/** A chapter marker within an episode */
@Serializable
data class Chapter(
    // seconds
    val startTime: Double,
    val title: String,
    val img: String? = null,
    val url: String? = null,
    val relatedEpisodes: List<Episode>? = null,
)

/** A recommended podcast recommendation */
@Serializable
data class PodrollItem(val title: String, val url: String, val uuid: String? = null)
