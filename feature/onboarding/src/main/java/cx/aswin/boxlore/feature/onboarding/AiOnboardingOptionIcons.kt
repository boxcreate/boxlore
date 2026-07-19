package cx.aswin.boxlore.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.ui.graphics.vector.ImageVector

internal fun getOptionIcons(option: String): Pair<ImageVector, ImageVector> {
    val lower = option.lowercase()
    return when {
        // Custom Circuit Breaker Options
        lower.contains("build my feed now") || lower.contains("build feed now") -> {
            Pair(Icons.Outlined.AutoAwesome, Icons.Rounded.AutoAwesome)
        }
        lower.contains("more about my tastes") || lower.contains("describe my tastes") -> {
            Pair(Icons.Outlined.Tune, Icons.Rounded.Tune)
        }

        // Story / Narrative / True Crime
        lower.contains("story") ||
            lower.contains("mystery") ||
            lower.contains("fiction") ||
            lower.contains("crime") ||
            lower.contains("detective") ||
            lower.contains("thriller") ||
            lower.contains("narrative") ||
            lower.contains("case") ||
            lower.contains("murder") ||
            lower.contains("novel") ||
            lower.contains("book") -> {
            Pair(Icons.Outlined.AutoStories, Icons.Rounded.AutoStories)
        }

        // Learn / Info / Science / Deep Dive / History / Tech / Business
        lower.contains("learn") ||
            lower.contains("deep") ||
            lower.contains("science") ||
            lower.contains("tech") ||
            lower.contains("mind") ||
            lower.contains("knowledge") ||
            lower.contains("teach") ||
            lower.contains("educat") ||
            lower.contains("history") ||
            lower.contains("documentary") ||
            lower.contains("space") ||
            lower.contains("fact") ||
            lower.contains("business") ||
            lower.contains("career") ||
            lower.contains("finance") ||
            lower.contains("explain") ||
            lower.contains("intellect") ||
            lower.contains("discover") ||
            lower.contains("explore") ||
            lower.contains("curious") -> {
            Pair(Icons.Outlined.Lightbulb, Icons.Rounded.Lightbulb)
        }

        // Conversation / Talk / Comedy
        lower.contains("comedy") ||
            lower.contains("conversation") ||
            lower.contains("chat") ||
            lower.contains("talk") ||
            lower.contains("host") ||
            lower.contains("interview") ||
            lower.contains("forum") ||
            lower.contains("banter") ||
            lower.contains("laugh") ||
            lower.contains("humor") ||
            lower.contains("discuss") ||
            lower.contains("society") ||
            lower.contains("culture") -> {
            Pair(Icons.Outlined.Forum, Icons.Rounded.Forum)
        }

        // Relax / Calm / Sleep / Spa
        lower.contains("relax") ||
            lower.contains("wind") ||
            lower.contains("sooth") ||
            lower.contains("sleep") ||
            lower.contains("spa") ||
            lower.contains("calm") ||
            lower.contains("quiet") ||
            lower.contains("meditat") ||
            lower.contains("mindful") ||
            lower.contains("peace") ||
            lower.contains("ambient") ||
            lower.contains("nature") ||
            lower.contains("chill") -> {
            Pair(Icons.Outlined.Spa, Icons.Rounded.Spa)
        }

        // News / Politics
        lower.contains("news") ||
            lower.contains("daily") ||
            lower.contains("current") ||
            lower.contains("today") ||
            lower.contains("politic") ||
            lower.contains("world") ||
            lower.contains("report") ||
            lower.contains("journalism") -> {
            Pair(Icons.Outlined.Newspaper, Icons.Rounded.Newspaper)
        }

        // Music
        lower.contains("music") ||
            lower.contains("song") ||
            lower.contains("audio") ||
            lower.contains("sound") ||
            lower.contains("melody") ||
            lower.contains("beat") -> {
            Pair(Icons.Outlined.MusicNote, Icons.Rounded.MusicNote)
        }

        // Sports
        lower.contains("sport") ||
            lower.contains("game") ||
            lower.contains("play") ||
            lower.contains("football") ||
            lower.contains("f1") ||
            lower.contains("race") ||
            lower.contains("athlete") ||
            lower.contains("match") ||
            lower.contains("league") -> {
            Pair(Icons.Outlined.EmojiEvents, Icons.Rounded.EmojiEvents)
        }

        // Health / Fitness
        lower.contains("health") ||
            lower.contains("fit") ||
            lower.contains("body") ||
            lower.contains("well") ||
            lower.contains("exercise") ||
            lower.contains("medicine") ||
            lower.contains("mental") ||
            lower.contains("doctor") -> {
            Pair(Icons.Outlined.MonitorHeart, Icons.Rounded.MonitorHeart)
        }

        // Default
        else -> {
            Pair(Icons.Outlined.Mic, Icons.Rounded.Mic)
        }
    }
}
