package cx.aswin.boxcast.feature.home.components

import androidx.compose.ui.graphics.Color

data class RadioStation(
    val id: String = "",
    val name: String,
    val genre: String,
    val tags: List<String> = emptyList(),
    val frequency: String,
    val location: String,
    val accentColor: Color,
    val imageUrl: String = "",
    val streamUrl: String = "",
    val country: String = "",
    val language: String = "",
    val bitrate: Int = 0,
    val codec: String = "",
    val votes: Int = 0
)
