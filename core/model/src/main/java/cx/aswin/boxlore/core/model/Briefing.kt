package cx.aswin.boxlore.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Briefing(
    val date: String,
    val region: String,
    val title: String,
    val script: String,
    val audioUrl: String,
    val coverUrl: String,
    // Signed asset URLs from the server; fall back to hand-built URLs when absent
    val chaptersUrl: String? = null,
    val transcriptUrl: String? = null,
    val sources: List<BriefingSource> = emptyList()
)

@Serializable
data class BriefingSource(
    val title: String,
    val url: String
)
