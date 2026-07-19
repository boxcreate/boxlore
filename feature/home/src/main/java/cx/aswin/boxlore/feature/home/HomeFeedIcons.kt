package cx.aswin.boxlore.feature.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.ui.graphics.vector.ImageVector

private val homeSectionIcons: Map<String, ImageVector> =
    mapOf(
        "news" to Icons.AutoMirrored.Rounded.Article,
        "bolt" to Icons.Rounded.Bolt,
        "commute" to Icons.Rounded.DirectionsCar,
        "devices" to Icons.Rounded.Devices,
        "neurology" to Icons.Rounded.Psychology,
        "science" to Icons.Rounded.Science,
        "sentiment_very_satisfied" to Icons.Rounded.SentimentVerySatisfied,
        "sports" to Icons.Rounded.SportsSoccer,
        "history_edu" to Icons.Rounded.HistoryEdu,
        "record_voice_over" to Icons.Rounded.RecordVoiceOver,
        "movie" to Icons.Rounded.Movie,
        "moon" to Icons.Rounded.NightsStay,
        "mystery" to Icons.Rounded.Search,
        "explore" to Icons.Rounded.Explore,
    )

internal fun String?.toHomeSectionIcon(): ImageVector =
    homeSectionIcons[this?.lowercase()] ?: Icons.Rounded.School
