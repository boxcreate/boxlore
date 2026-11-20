package cx.aswin.boxlore.core.designsystem.icon

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.rounded.Folder
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
import cx.aswin.boxlore.core.designsystem.R

/**
 * An icon a folder or genre tag can wear. [key] is stored; [label] is its English name, which icon
 * search matches; [labelRes] names it for the listener (the icon's screen-reader label).
 */
data class GenreIconItem(
    val key: String,
    val icon: ImageVector,
    val label: String,
    @StringRes val labelRes: Int,
)

object GenreIcons {
    val all: List<GenreIconItem> = listOf(
        GenreIconItem("mic", Icons.Rounded.Mic, "Mic", R.string.genre_icon_mic),
        GenreIconItem("headphones", Icons.Rounded.Headphones, "Headphones", R.string.genre_icon_headphones),
        GenreIconItem("music", Icons.Rounded.MusicNote, "Music", R.string.genre_icon_music),
        GenreIconItem("movie", Icons.Rounded.Movie, "Movie", R.string.genre_icon_movie),
        GenreIconItem("gaming", Icons.Rounded.SportsEsports, "Gaming", R.string.genre_icon_gaming),
        GenreIconItem("code", Icons.Rounded.Code, "Code", R.string.genre_icon_code),
        GenreIconItem("tech", Icons.Rounded.Computer, "Tech", R.string.genre_icon_tech),
        GenreIconItem("bulb", Icons.Rounded.Lightbulb, "Ideas", R.string.genre_icon_bulb),
        GenreIconItem("star", Icons.Rounded.Star, "Star", R.string.genre_icon_star),
        GenreIconItem("fire", Icons.Rounded.Whatshot, "Fire", R.string.genre_icon_fire),
        GenreIconItem("science", Icons.Rounded.Science, "Science", R.string.genre_icon_science),
        GenreIconItem("book", Icons.Rounded.AutoStories, "Book", R.string.genre_icon_book),
        GenreIconItem("health", Icons.Rounded.Favorite, "Health", R.string.genre_icon_health),
        GenreIconItem("finance", Icons.Rounded.AttachMoney, "Finance", R.string.genre_icon_finance),
        GenreIconItem("news", Icons.Rounded.Newspaper, "News", R.string.genre_icon_news),
        GenreIconItem("sports", Icons.Rounded.SportsBaseball, "Sports", R.string.genre_icon_sports),
        GenreIconItem("comedy", Icons.Rounded.SentimentVerySatisfied, "Comedy", R.string.genre_icon_comedy),
        GenreIconItem("history", Icons.Rounded.AccountBalance, "History", R.string.genre_icon_history),
        GenreIconItem("art", Icons.Rounded.Palette, "Art", R.string.genre_icon_art),
        GenreIconItem("education", Icons.Rounded.School, "Education", R.string.genre_icon_education),
        GenreIconItem("crime", Icons.Rounded.Fingerprint, "True Crime", R.string.genre_icon_crime),
        GenreIconItem("chat", Icons.Rounded.Forum, "Chat", R.string.genre_icon_chat),
        GenreIconItem("travel", Icons.Rounded.Explore, "Travel", R.string.genre_icon_travel),
        GenreIconItem("food", Icons.Rounded.Restaurant, "Food", R.string.genre_icon_food),
        GenreIconItem("nature", Icons.Rounded.Park, "Nature", R.string.genre_icon_nature),
        GenreIconItem("family", Icons.Rounded.Face, "Family", R.string.genre_icon_family),
        GenreIconItem("business", Icons.Rounded.Work, "Business", R.string.genre_icon_business),
        GenreIconItem("government", Icons.Rounded.Gavel, "Government", R.string.genre_icon_government),
        GenreIconItem("tag", Icons.Rounded.LocalOffer, "Tag", R.string.genre_icon_tag),
        GenreIconItem("category", Icons.Rounded.Category, "Category", R.string.genre_icon_category),
        GenreIconItem("folder", Icons.Rounded.Folder, "Folder", R.string.genre_icon_folder),
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
        listOf("tech", "technology", "computer") to Icons.Rounded.Computer,
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

    fun findExactKeywordIcon(genre: String?): ImageVector? {
        if (genre.isNullOrBlank()) return null
        val normalized = genre.trim().lowercase()
        return GENRE_KEYWORDS.firstOrNull { (keywords, _) ->
            keywords.any { it.equals(normalized, ignoreCase = true) }
        }?.second
    }

    fun defaultGenreIcon(genre: String?): ImageVector {
        if (genre.isNullOrBlank()) return Icons.Rounded.Category
        val normalized = genre.trim().lowercase()
        return GENRE_KEYWORDS.firstOrNull { (keywords, _) ->
            keywords.any { matchesKeyword(normalized, it) }
        }?.second ?: Icons.Rounded.Category
    }

    fun iconOrFallback(key: String?, fallbackGenre: String? = null): ImageVector =
        findIcon(key) ?: defaultGenreIcon(fallbackGenre)

    fun defaultFolderIcon(): ImageVector = Icons.Rounded.Folder

    fun folderIconOrFallback(key: String?): ImageVector =
        findIcon(key) ?: defaultFolderIcon()

    private fun GenreIconItem.matchesPrefix(query: String): Boolean =
        key.lowercase().startsWith(query) || label.lowercase().startsWith(query)

    private fun GenreIconItem.matchesSubstring(query: String): Boolean =
        key.lowercase().contains(query) || label.lowercase().contains(query)

    private fun findKeywordMatches(query: String): List<GenreIconItem> =
        GENRE_KEYWORDS.mapNotNull { (keywords, iconVector) ->
            val matches = keywords.any { it.contains(query) || query.contains(it) }
            if (matches) all.firstOrNull { it.icon == iconVector } else null
        }

    fun suggestIcons(query: String): List<GenreIconItem> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return emptyList()

        val prefixMatches = all.filter { it.matchesPrefix(trimmed) }
        val keywordMatches = findKeywordMatches(trimmed)
        val substringMatches = all.filter { it.matchesSubstring(trimmed) }

        return (prefixMatches + keywordMatches + substringMatches).distinct().take(8)
    }
}
