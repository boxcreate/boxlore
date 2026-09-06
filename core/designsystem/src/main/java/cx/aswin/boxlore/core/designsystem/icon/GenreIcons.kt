package cx.aswin.boxlore.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LocalOffer
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SportsBaseball
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

data class GenreIconItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
)

object GenreIcons {
    val all: List<GenreIconItem> = listOf(
        GenreIconItem("mic", Icons.Rounded.Mic, "Mic"),
        GenreIconItem("headphones", Icons.Rounded.Headphones, "Headphones"),
        GenreIconItem("music", Icons.Rounded.MusicNote, "Music"),
        GenreIconItem("movie", Icons.Rounded.Movie, "Movie"),
        GenreIconItem("gaming", Icons.Rounded.SportsEsports, "Gaming"),
        GenreIconItem("code", Icons.Rounded.Code, "Code"),
        GenreIconItem("tech", Icons.Rounded.Computer, "Tech"),
        GenreIconItem("bulb", Icons.Rounded.Lightbulb, "Ideas"),
        GenreIconItem("star", Icons.Rounded.Star, "Star"),
        GenreIconItem("fire", Icons.Rounded.Whatshot, "Fire"),
        GenreIconItem("science", Icons.Rounded.Science, "Science"),
        GenreIconItem("book", Icons.Rounded.AutoStories, "Book"),
        GenreIconItem("health", Icons.Rounded.Favorite, "Health"),
        GenreIconItem("finance", Icons.Rounded.AttachMoney, "Finance"),
        GenreIconItem("news", Icons.Rounded.Newspaper, "News"),
        GenreIconItem("sports", Icons.Rounded.SportsBaseball, "Sports"),
        GenreIconItem("comedy", Icons.Rounded.SentimentVerySatisfied, "Comedy"),
        GenreIconItem("history", Icons.Rounded.AccountBalance, "History"),
        GenreIconItem("art", Icons.Rounded.Palette, "Art"),
        GenreIconItem("education", Icons.Rounded.School, "Education"),
        GenreIconItem("crime", Icons.Rounded.Fingerprint, "True Crime"),
        GenreIconItem("chat", Icons.Rounded.Forum, "Chat"),
        GenreIconItem("travel", Icons.Rounded.Explore, "Travel"),
        GenreIconItem("food", Icons.Rounded.Restaurant, "Food"),
        GenreIconItem("nature", Icons.Rounded.Park, "Nature"),
        GenreIconItem("family", Icons.Rounded.Face, "Family"),
        GenreIconItem("business", Icons.Rounded.Work, "Business"),
        GenreIconItem("government", Icons.Rounded.Gavel, "Government"),
        GenreIconItem("tag", Icons.Rounded.LocalOffer, "Tag"),
        GenreIconItem("category", Icons.Rounded.Category, "Category"),
    )

    private val byKey: Map<String, ImageVector> = all.associate { it.key.lowercase() to it.icon }

    fun findIcon(key: String?): ImageVector? {
        if (key.isNullOrBlank()) return null
        val normalized = key.trim().lowercase()
        return byKey[normalized] ?: when (normalized) {
            "technology" -> Icons.Rounded.Computer
            "ideas" -> Icons.Rounded.Lightbulb
            "games" -> Icons.Rounded.SportsEsports
            "books" -> Icons.Rounded.AutoStories
            "money" -> Icons.Rounded.AttachMoney
            "true crime" -> Icons.Rounded.Fingerprint
            "talk" -> Icons.Rounded.Forum
            "work" -> Icons.Rounded.Work
            else -> null
        }
    }

    private val GENRE_KEYWORDS: List<Pair<List<String>, ImageVector>> = listOf(
        listOf("music") to Icons.Rounded.MusicNote,
        listOf("comedy") to Icons.Rounded.SentimentVerySatisfied,
        listOf("sport", "sports") to Icons.Rounded.SportsBaseball,
        listOf("science") to Icons.Rounded.Science,
        listOf("tech", "computer") to Icons.Rounded.Computer,
        listOf("news") to Icons.Rounded.Newspaper,
        listOf("health", "fitness") to Icons.Rounded.Favorite,
        listOf("history") to Icons.Rounded.AccountBalance,
        listOf("art", "arts", "design") to Icons.Rounded.Palette,
        listOf("education") to Icons.Rounded.School,
        listOf("tv", "film", "movie", "movies") to Icons.Rounded.Movie,
        listOf("fiction", "story", "stories", "book", "books") to Icons.Rounded.AutoStories,
        listOf("game", "games", "gaming") to Icons.Rounded.SportsEsports,
        listOf("religion", "spiritual") to Icons.Rounded.Star,
        listOf("family", "kid", "kids") to Icons.Rounded.Face,
        listOf("business", "work", "startup", "startups") to Icons.Rounded.Work,
        listOf("government", "gavel") to Icons.Rounded.Gavel,
        listOf("crime") to Icons.Rounded.Fingerprint,
        listOf("chat", "talk", "society", "culture") to Icons.Rounded.Forum,
        listOf("finance", "money", "invest") to Icons.Rounded.AttachMoney,
    )

    private fun matchesKeyword(genre: String, keyword: String): Boolean =
        Regex("""(^|[^\p{L}\p{N}])${Regex.escape(keyword)}($|[^\p{L}\p{N}])""")
            .containsMatchIn(genre)

    fun defaultGenreIcon(genre: String?): ImageVector {
        if (genre.isNullOrBlank()) return Icons.Rounded.Category
        val normalized = genre.trim().lowercase()
        return GENRE_KEYWORDS.firstOrNull { (keywords, _) ->
            keywords.any { matchesKeyword(normalized, it) }
        }?.second ?: Icons.Rounded.Category
    }

    fun iconOrFallback(key: String?, fallbackGenre: String? = null): ImageVector =
        findIcon(key) ?: defaultGenreIcon(fallbackGenre)
}
