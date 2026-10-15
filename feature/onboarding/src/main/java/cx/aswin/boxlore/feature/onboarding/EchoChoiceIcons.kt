package cx.aswin.boxlore.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

/**
 * Icons for Echo's later choices, matched by whole words (short keys) or word stems (keys of five
 * or more letters), so "space" never matches "spa". Every choice gets an icon: unmatched ones take
 * a distinct general-purpose icon that suggests no topic, so a row is never blank or misleading.
 */
internal object EchoChoiceIcons {
    private val groups: List<Pair<List<String>, ImageVector>> = listOf(
        // Tone and mood come first: "lighthearted history" is about tone, not the topic.
        listOf("optimis", "wonder", "upbeat", "hopeful", "uplifting", "inspiring", "inspir") to Icons.Rounded.WbSunny,
        listOf("calm", "contemplat", "reflect", "relax", "soothing", "gentle", "quiet", "chill", "cozy", "cosy") to Icons.Rounded.Eco,
        listOf("humor", "humour", "funny", "lighthearted", "light", "lightness", "laugh", "playful", "witty", "comedy", "comedic") to Icons.Rounded.SentimentVerySatisfied,
        listOf("analysi", "analytical", "serious", "rigorous", "nerdy", "detailed", "explain", "explainer") to Icons.Rounded.Insights,
        listOf("sleep", "bedtime", "night") to Icons.Rounded.Bedtime,
        listOf("commute", "commuting", "drive", "driving") to Icons.Rounded.DirectionsCar,
        listOf("workout", "gym", "running", "exercise", "fitness") to Icons.Rounded.FitnessCenter,
        listOf("short", "quick", "bite", "minutes", "brief") to Icons.Rounded.Timer,
        listOf("long", "longform", "hours", "marathon", "deep") to Icons.Rounded.Schedule,
        listOf("interview", "interviews", "conversation", "conversations", "chat", "chats", "talk", "banter", "discussion", "panel", "hosts", "guests") to Icons.Rounded.Forum,
        listOf("solo", "narrat", "narrated", "storyteller", "monologue") to Icons.Rounded.Mic,
        listOf("story", "stories", "fiction", "drama", "novel", "audiobook", "narrative", "serial", "serialized") to Icons.Rounded.AutoStories,
        listOf("crime", "mystery", "mysteries", "murder", "detective", "case", "cases", "heist", "unsolved") to Icons.Rounded.Fingerprint,
        listOf("science", "physics", "biology", "chemistry", "space", "astronomy", "cosmos", "nature", "climate") to Icons.Rounded.Science,
        listOf("tech", "technology", "computer", "computers", "software", "coding", "gadgets", "startup", "startups") to Icons.Rounded.Computer,
        listOf("history", "historical", "ancient", "empire", "empires", "medieval", "war", "wars", "past") to Icons.Rounded.AccountBalance,
        listOf("news", "politic", "politics", "current", "headlines", "journalism", "election", "elections") to Icons.Rounded.Newspaper,
        listOf("world", "global", "travel", "culture", "cultures", "society") to Icons.Rounded.Public,
        listOf("business", "career", "careers", "work", "entrepreneur", "leadership", "marketing") to Icons.Rounded.Work,
        listOf("money", "finance", "investing", "invest", "economy", "economics", "markets") to Icons.Rounded.AttachMoney,
        listOf("health", "wellbeing", "wellness", "mindful", "meditat", "habits") to Icons.Rounded.SelfImprovement,
        listOf("psychology", "mind", "brain", "mental", "relationships", "philosophy") to Icons.Rounded.Psychology,
        listOf("learn", "learning", "education", "lessons", "teach", "study") to Icons.Rounded.School,
        listOf("ideas", "curious", "curiosity", "facts", "trivia", "discover") to Icons.Rounded.Lightbulb,
        listOf("music", "songs", "album", "albums", "musician", "musicians") to Icons.Rounded.MusicNote,
        listOf("film", "films", "movie", "movies", "tv", "television", "shows", "pop") to Icons.Rounded.Movie,
        listOf("art", "arts", "design", "books", "literature", "writing") to Icons.Rounded.Palette,
        listOf("sport", "sports", "football", "soccer", "cricket", "basketball", "f1", "racing", "athletes") to Icons.Rounded.EmojiEvents,
        listOf("game", "games", "gaming", "videogames") to Icons.Rounded.SportsEsports,
        listOf("food", "cooking", "recipes", "chef", "chefs", "wine") to Icons.Rounded.Restaurant,
        listOf("audio", "sound", "immersive", "soundscape") to Icons.Rounded.Headphones,
    )

    /** Topic-free icons for unmatched choices, never repeating within a question. */
    val fallbacks: List<ImageVector> = listOf(Icons.Rounded.AutoAwesome, Icons.Rounded.Lightbulb, Icons.Rounded.Explore, Icons.Rounded.Interests)

    fun match(choice: String): ImageVector? {
        val words = choice.substringBefore('|').lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return null
        return groups.firstOrNull { (keys, _) -> keys.any { key -> words.any { word -> matches(word, key) } } }?.second
    }

    /** One icon per choice: its match, or the next unused fallback. */
    fun forChoices(choices: List<String>): List<ImageVector> {
        val matched = choices.map(::match)
        val spare = fallbacks.filterNot { it in matched }.toMutableList()
        return matched.mapIndexed { index, icon -> icon ?: spare.removeFirstOrNull() ?: fallbacks[index % fallbacks.size] }
    }

    private fun matches(word: String, key: String): Boolean = if (key.length >= 5) word.startsWith(key) else word == key
}
