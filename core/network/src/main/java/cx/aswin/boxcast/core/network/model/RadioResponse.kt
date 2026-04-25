package cx.aswin.boxcast.core.network.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class RadioStationResponse(
    @SerialName("status") val status: String = "false",
    @SerialName("stations") val stations: List<RadioStationItem> = emptyList()
)

@Serializable
data class RadioStationItem(
    @SerialName("id") val id: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("image") val image: String = "",
    @SerialName("streamUrl") val streamUrl: String = "",
    @SerialName("homepage") val homepage: String = "",
    @SerialName("tags") val tags: List<String> = emptyList(),
    @SerialName("country") val country: String = "",
    @SerialName("language") val language: String = "",
    @SerialName("codec") val codec: String = "",
    @SerialName("bitrate") val bitrate: Int = 0,
    @SerialName("votes") val votes: Int = 0
)

@Serializable
data class RadioLocateResponse(
    @SerialName("country") val country: String = "US",
    @SerialName("languages") val languages: List<String> = emptyList()
)
