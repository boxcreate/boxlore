package cx.aswin.boxlore.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

internal fun getCategoryIcon(title: String): ImageVector {
    val lower = title.lowercase()
    return categoryIconRules()
        .firstOrNull { (keywords, _) -> keywords.any(lower::contains) }
        ?.second
        ?: Icons.Rounded.AutoAwesome
}

private fun categoryIconRules(): List<Pair<Set<String>, ImageVector>> =
    listOf(
        setOf("crime", "murder", "detective", "mystery", "thriller", "spooky", "horror", "investigat") to Icons.Rounded.Fingerprint,
        setOf("tech", "computer", "digital", "ai", "innovation", "future") to Icons.Rounded.Computer,
        setOf("comedy", "funny", "laugh", "humor", "joke", "conversation", "talk", "chat") to Icons.Rounded.SentimentVerySatisfied,
        setOf("news", "daily", "politics", "world", "current") to Icons.Rounded.Newspaper,
        setOf("business", "money", "finance", "work", "career", "startup", "investing", "investment") to Icons.Rounded.Work,
        setOf("sports", "game", "ball", "football", "basketball", "match") to Icons.Rounded.EmojiEvents,
        setOf("health", "mind", "body", "meditation", "sleep", "relax", "yoga", "wellness", "heart") to Icons.Rounded.MonitorHeart,
        setOf("history", "ancient", "past", "museum", "heritage") to Icons.Rounded.AccountBalance,
        setOf("arts", "design", "paint", "creative", "culture") to Icons.Rounded.Palette,
        setOf("science", "physics", "bio", "space", "lab", "research") to Icons.Rounded.Science,
        setOf("fiction", "story", "stories", "drama", "book", "read", "novel") to Icons.Rounded.AutoStories,
        setOf("music", "song", "audio", "rhythm", "instrument") to Icons.Rounded.MusicNote,
        setOf("religion", "spirit", "faith", "god", "soul") to Icons.Rounded.SelfImprovement,
        setOf("kids", "family", "parent", "child") to Icons.Rounded.ChildCare,
        setOf("leisure", "weekend", "chill", "hobby") to Icons.Rounded.Weekend,
        setOf("government", "law", "court", "gavel") to Icons.Rounded.Gavel,
    )
