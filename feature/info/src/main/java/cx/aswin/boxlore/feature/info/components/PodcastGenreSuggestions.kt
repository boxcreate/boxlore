package cx.aswin.boxlore.feature.info.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoStories
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

/**
 * Metadata for a suggested genre or topic chip in [PodcastGenreEditSheet].
 * Pairs the display tag with its default matching icon from [cx.aswin.boxlore.core.designsystem.icon.GenreIcons].
 */
data class GenreSuggestion(
    val name: String,
    val iconKey: String,
    val icon: ImageVector,
    val keywords: List<String> = emptyList(),
)

/**
 * Default catalog genres (canonical genres from [cx.aswin.boxlore.core.model.PodcastGenres]
 * and library filter items) alongside popular topic tags.
 */
val ALL_GENRE_SUGGESTIONS: List<GenreSuggestion> = listOf(
    // Canonical / Standard Catalog Genres
    GenreSuggestion(
        name = "News",
        iconKey = "news",
        icon = Icons.Rounded.Newspaper,
        keywords = listOf("daily", "current events", "journalism", "politics", "world", "bulletin", "headline"),
    ),
    GenreSuggestion(
        name = "Technology",
        iconKey = "tech",
        icon = Icons.Rounded.Computer,
        keywords = listOf("tech", "computers", "software", "coding", "ai", "hardware", "internet", "gadgets", "programming"),
    ),
    GenreSuggestion(
        name = "Tech",
        iconKey = "tech",
        icon = Icons.Rounded.Computer,
        keywords = listOf("technology", "computers", "software", "coding", "ai", "hardware"),
    ),
    GenreSuggestion(
        name = "Business",
        iconKey = "business",
        icon = Icons.Rounded.Work,
        keywords = listOf("finance", "money", "investing", "startup", "economy", "entrepreneurship", "work", "career"),
    ),
    GenreSuggestion(
        name = "Comedy",
        iconKey = "comedy",
        icon = Icons.Rounded.SentimentVerySatisfied,
        keywords = listOf("funny", "humor", "standup", "jokes", "satire", "laugh", "improv"),
    ),
    GenreSuggestion(
        name = "True Crime",
        iconKey = "crime",
        icon = Icons.Rounded.Fingerprint,
        keywords = listOf("crime", "murder", "investigation", "mystery", "forensics", "detective", "serial"),
    ),
    GenreSuggestion(
        name = "Sports",
        iconKey = "sports",
        icon = Icons.Rounded.SportsBaseball,
        keywords = listOf("sport", "football", "basketball", "soccer", "baseball", "athletics", "training", "fitness"),
    ),
    GenreSuggestion(
        name = "Health",
        iconKey = "health",
        icon = Icons.Rounded.Favorite,
        keywords = listOf("fitness", "wellness", "mental health", "medical", "nutrition", "workout", "medicine"),
    ),
    GenreSuggestion(
        name = "History",
        iconKey = "history",
        icon = Icons.Rounded.AccountBalance,
        keywords = listOf("past", "historical", "war", "ancient", "biography", "archives"),
    ),
    GenreSuggestion(
        name = "Arts",
        iconKey = "art",
        icon = Icons.Rounded.Palette,
        keywords = listOf("art", "design", "creativity", "culture", "visual", "painting", "illustration"),
    ),
    GenreSuggestion(
        name = "Society & Culture",
        iconKey = "chat",
        icon = Icons.Rounded.Forum,
        keywords = listOf("society", "culture", "talk", "chat", "lifestyle", "philosophy", "people", "relationships"),
    ),
    GenreSuggestion(
        name = "Society",
        iconKey = "chat",
        icon = Icons.Rounded.Forum,
        keywords = listOf("society & culture", "culture", "talk", "chat", "lifestyle", "people"),
    ),
    GenreSuggestion(
        name = "Education",
        iconKey = "education",
        icon = Icons.Rounded.School,
        keywords = listOf("learning", "school", "teaching", "academy", "skills", "how to", "knowledge", "courses"),
    ),
    GenreSuggestion(
        name = "Science",
        iconKey = "science",
        icon = Icons.Rounded.Science,
        keywords = listOf("physics", "biology", "chemistry", "space", "astronomy", "research", "nature", "neuroscience"),
    ),
    GenreSuggestion(
        name = "TV & Film",
        iconKey = "movie",
        icon = Icons.Rounded.Movie,
        keywords = listOf("movie", "movies", "film", "films", "cinema", "tv", "television", "hollywood", "shows", "series"),
    ),
    GenreSuggestion(
        name = "Fiction",
        iconKey = "book",
        icon = Icons.Rounded.AutoStories,
        keywords = listOf("stories", "story", "audio drama", "books", "literature", "novels", "audiobook", "scifi"),
    ),
    GenreSuggestion(
        name = "Music",
        iconKey = "music",
        icon = Icons.Rounded.MusicNote,
        keywords = listOf("songs", "musician", "bands", "albums", "audio", "sound", "track", "concert"),
    ),
    GenreSuggestion(
        name = "Religion & Spirituality",
        iconKey = "star",
        icon = Icons.Rounded.Star,
        keywords = listOf("religion", "spiritual", "faith", "god", "meditation", "buddhism", "christianity", "prayer", "zen"),
    ),
    GenreSuggestion(
        name = "Kids & Family",
        iconKey = "family",
        icon = Icons.Rounded.Face,
        keywords = listOf("family", "kids", "children", "parenting", "bedtime", "childhood"),
    ),
    GenreSuggestion(
        name = "Leisure",
        iconKey = "headphones",
        icon = Icons.Rounded.Headphones,
        keywords = listOf("hobbies", "relaxation", "crafts", "automotive", "aviation", "chill", "video games"),
    ),
    GenreSuggestion(
        name = "Government",
        iconKey = "government",
        icon = Icons.Rounded.Gavel,
        keywords = listOf("gov", "politics", "law", "policy", "court", "elections", "democracy"),
    ),

    // Popular tags from GenreIcons
    GenreSuggestion(
        name = "Gaming",
        iconKey = "gaming",
        icon = Icons.Rounded.SportsEsports,
        keywords = listOf("game", "games", "video games", "esports", "playstation", "xbox", "nintendo", "pc gaming"),
    ),
    GenreSuggestion(
        name = "Coding",
        iconKey = "code",
        icon = Icons.Rounded.Code,
        keywords = listOf("code", "programming", "developer", "software", "engineering", "dev", "python", "kotlin"),
    ),
    GenreSuggestion(
        name = "Finance",
        iconKey = "finance",
        icon = Icons.Rounded.AttachMoney,
        keywords = listOf("money", "stocks", "crypto", "investing", "wealth", "markets", "banking", "economy"),
    ),
    GenreSuggestion(
        name = "Travel",
        iconKey = "travel",
        icon = Icons.Rounded.Explore,
        keywords = listOf("journey", "adventure", "tourism", "vacation", "explore", "flight", "places", "road trip"),
    ),
    GenreSuggestion(
        name = "Food",
        iconKey = "food",
        icon = Icons.Rounded.Restaurant,
        keywords = listOf("cooking", "culinary", "recipes", "dining", "baking", "chef", "restaurant", "drinks"),
    ),
    GenreSuggestion(
        name = "Nature",
        iconKey = "nature",
        icon = Icons.Rounded.Park,
        keywords = listOf("environment", "wildlife", "outdoors", "animals", "earth", "climate", "plants", "forest"),
    ),
    GenreSuggestion(
        name = "Interviews",
        iconKey = "mic",
        icon = Icons.Rounded.Mic,
        keywords = listOf("talk", "conversation", "interview", "podcast", "host", "dialogue", "chat"),
    ),
    GenreSuggestion(
        name = "Deep Dives",
        iconKey = "bulb",
        icon = Icons.Rounded.Lightbulb,
        keywords = listOf("ideas", "curiosity", "analysis", "thoughts", "explaining", "investigative", "in depth"),
    ),
    GenreSuggestion(
        name = "Favorites",
        iconKey = "star",
        icon = Icons.Rounded.Star,
        keywords = listOf("best", "starred", "top", "favorite", "saved", "loved", "picks"),
    ),
    GenreSuggestion(
        name = "Trending",
        iconKey = "fire",
        icon = Icons.Rounded.Whatshot,
        keywords = listOf("hot", "popular", "viral", "fire", "hype", "buzz"),
    ),
)

/**
 * Filters and ranks genre suggestions according to the user's typed search query.
 * Exact name matches rank first, followed by prefix matches, keyword matches, and substrings.
 * When [query] is blank, returns the full catalog in standard order.
 */
fun filterGenreSuggestions(
    query: String,
    allSuggestions: List<GenreSuggestion> = ALL_GENRE_SUGGESTIONS,
): List<GenreSuggestion> {
    val trimmed = query.trim().lowercase()
    if (trimmed.isEmpty()) return allSuggestions

    return allSuggestions
        .mapNotNull { suggestion ->
            val nameLower = suggestion.name.lowercase()
            val score = when {
                nameLower == trimmed -> 0
                nameLower.startsWith(trimmed) -> 1
                suggestion.keywords.any { it.equals(trimmed, ignoreCase = true) } -> 2
                nameLower.contains(trimmed) -> 3
                suggestion.keywords.any { it.startsWith(trimmed, ignoreCase = true) } -> 4
                suggestion.keywords.any { it.contains(trimmed, ignoreCase = true) } -> 5
                suggestion.iconKey.contains(trimmed) -> 6
                else -> null
            }
            if (score != null) suggestion to score else null
        }
        .sortedBy { it.second }
        .map { it.first }
}
