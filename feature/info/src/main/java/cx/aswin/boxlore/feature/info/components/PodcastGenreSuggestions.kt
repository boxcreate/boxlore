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
        keywords = listOf(
            "daily", "current events", "journalism", "politics", "world", "bulletin",
            "headline", "breaking news", "breaking", "morning news", "morning",
            "international", "newspaper", "press", "report", "reporting", "updates",
            "briefing", "global", "national", "investigative journalism", "broadcast",
        ),
    ),
    GenreSuggestion(
        name = "Technology",
        iconKey = "tech",
        icon = Icons.Rounded.Computer,
        keywords = listOf(
            "tech", "computers", "software", "coding", "ai", "hardware", "internet",
            "gadgets", "programming", "developer", "technology and science", "technology & science",
            "engineering", "cybersecurity", "machine learning", "data science", "cloud",
            "mobile", "android", "ios", "crypto", "web", "robotics", "semiconductor",
            "silicon valley", "devops", "apps", "open source", "artificial intelligence",
            "it", "algorithms", "tech news",
        ),
    ),
    GenreSuggestion(
        name = "Tech",
        iconKey = "tech",
        icon = Icons.Rounded.Computer,
        keywords = listOf(
            "technology", "computers", "software", "coding", "ai", "hardware",
            "programming", "developer", "cyber", "internet", "gadgets", "apps",
            "artificial intelligence", "data", "engineering", "tech news",
        ),
    ),
    GenreSuggestion(
        name = "Business",
        iconKey = "business",
        icon = Icons.Rounded.Work,
        keywords = listOf(
            "finance", "money", "investing", "startup", "economy", "entrepreneurship",
            "work", "career", "economics", "stock market", "stocks", "wall street",
            "venture capital", "vc", "real estate", "management", "leadership",
            "marketing", "sales", "wealth", "banking", "commerce", "ecommerce",
            "consulting", "corporate", "trade", "jobs", "small business", "founder",
            "ceo", "business news",
        ),
    ),
    GenreSuggestion(
        name = "Comedy",
        iconKey = "comedy",
        icon = Icons.Rounded.SentimentVerySatisfied,
        keywords = listOf(
            "funny", "humor", "humour", "standup", "stand-up", "jokes", "satire",
            "laugh", "improv", "roast", "parody", "hilarious", "comedian", "sketch",
            "wit", "silly", "banter", "dark humor", "comedy podcast", "irony", "entertaining",
        ),
    ),
    GenreSuggestion(
        name = "True Crime",
        iconKey = "crime",
        icon = Icons.Rounded.Fingerprint,
        keywords = listOf(
            "crime", "murder", "investigation", "mystery", "forensics", "detective",
            "serial killer", "serial", "unsolved", "true-crime", "truecrime", "cold case",
            "fbi", "police", "heist", "courtroom", "scam", "con artist", "fraud",
            "thriller", "criminal", "espionage", "interrogation", "whodunit", "law enforcement",
        ),
    ),
    GenreSuggestion(
        name = "Sports",
        iconKey = "sports",
        icon = Icons.Rounded.SportsBaseball,
        keywords = listOf(
            "sport", "football", "basketball", "soccer", "baseball", "athletics",
            "training", "fitness", "cricket", "tennis", "formula 1", "f1", "racing",
            "hockey", "nfl", "nba", "premier league", "golf", "mma", "ufc", "boxing",
            "olympics", "running", "cycling", "marathon", "gym", "motorsport",
            "sports talk", "championship", "world cup",
        ),
    ),
    GenreSuggestion(
        name = "Health",
        iconKey = "health",
        icon = Icons.Rounded.Favorite,
        keywords = listOf(
            "fitness", "wellness", "mental health", "medical", "nutrition", "workout",
            "medicine", "health and fitness", "health & fitness", "diet", "mindfulness",
            "therapy", "psychology", "doctor", "exercise", "longevity", "sleep",
            "anxiety", "meditation", "gut health", "habits", "bodybuilding", "healing",
            "healthcare", "recovery", "wellbeing", "stress",
        ),
    ),
    GenreSuggestion(
        name = "History",
        iconKey = "history",
        icon = Icons.Rounded.AccountBalance,
        keywords = listOf(
            "past", "historical", "war", "ancient", "biography", "archives", "world war",
            "civilization", "empire", "medieval", "monarchy", "archaeology", "cold war",
            "roman", "greek", "revolution", "presidents", "military", "retro", "origins",
            "heritage", "centuries", "historian", "timelines",
        ),
    ),
    GenreSuggestion(
        name = "Arts",
        iconKey = "art",
        icon = Icons.Rounded.Palette,
        keywords = listOf(
            "art", "design", "creativity", "culture", "visual", "painting", "illustration",
            "architecture", "fashion", "museum", "craft", "sculpture", "photography",
            "drawing", "graphic design", "theater", "theatre", "acting", "opera",
            "dance", "performing arts", "artist", "creative writing", "visual arts",
        ),
    ),
    GenreSuggestion(
        name = "Society & Culture",
        iconKey = "chat",
        icon = Icons.Rounded.Forum,
        keywords = listOf(
            "society", "culture", "talk", "chat", "lifestyle", "philosophy", "people",
            "relationships", "society and culture", "society & culture", "interviews",
            "dating", "identity", "documentary", "stories", "memoir", "subculture",
            "humanities", "anthropology", "commentary", "social issues", "feminism",
            "community", "conversations",
        ),
    ),
    GenreSuggestion(
        name = "Society",
        iconKey = "chat",
        icon = Icons.Rounded.Forum,
        keywords = listOf(
            "society & culture", "culture", "talk", "chat", "lifestyle", "people",
            "social", "relationships", "documentary", "conversations",
        ),
    ),
    GenreSuggestion(
        name = "Education",
        iconKey = "education",
        icon = Icons.Rounded.School,
        keywords = listOf(
            "learning", "school", "teaching", "academy", "skills", "how to", "knowledge",
            "courses", "college", "university", "self improvement", "productivity",
            "languages", "grammar", "tutorials", "educational", "training", "study",
            "masterclass", "lessons", "curiosity", "academic",
        ),
    ),
    GenreSuggestion(
        name = "Science",
        iconKey = "science",
        icon = Icons.Rounded.Science,
        keywords = listOf(
            "physics", "biology", "chemistry", "space", "astronomy", "research",
            "nature", "neuroscience", "genetics", "evolution", "quantum", "astrophysics",
            "nasa", "scientific", "climate", "environment", "ecology", "geology",
            "laboratory", "discoveries", "cosmos", "scientists",
        ),
    ),
    GenreSuggestion(
        name = "TV & Film",
        iconKey = "movie",
        icon = Icons.Rounded.Movie,
        keywords = listOf(
            "movie", "movies", "film", "films", "cinema", "tv", "television", "hollywood",
            "shows", "series", "tv and film", "tv & film", "reviews", "cinematography",
            "directors", "actors", "screenplay", "box office", "netflix", "hbo", "streaming",
            "critics", "blockbuster", "popcorn", "entertainment", "recap", "binge",
        ),
    ),
    GenreSuggestion(
        name = "Fiction",
        iconKey = "book",
        icon = Icons.Rounded.AutoStories,
        keywords = listOf(
            "stories", "story", "audio drama", "books", "literature", "novels",
            "audiobook", "scifi", "sci-fi", "sci fi", "science fiction", "drama",
            "fantasy", "storytelling", "folklore", "myths", "thriller fiction",
            "mystery fiction", "horror", "creepy", "short stories", "fiction series",
            "monsters", "supernatural",
        ),
    ),
    GenreSuggestion(
        name = "Music",
        iconKey = "music",
        icon = Icons.Rounded.MusicNote,
        keywords = listOf(
            "songs", "musician", "bands", "albums", "audio", "sound", "track", "concert",
            "hip hop", "rock", "jazz", "classical", "pop", "indie", "electronic", "guitar",
            "beats", "music history", "producers", "lyrics", "singers", "record label",
            "rap", "metal", "edm", "playlist",
        ),
    ),
    GenreSuggestion(
        name = "Religion & Spirituality",
        iconKey = "star",
        icon = Icons.Rounded.Star,
        keywords = listOf(
            "religion", "spiritual", "faith", "god", "meditation", "buddhism",
            "christianity", "prayer", "zen", "religion and spirituality",
            "religion & spirituality", "bible", "islam", "judaism", "theology",
            "devotional", "mindfulness", "gospel", "soul", "enlightenment",
            "hinduism", "pastor", "church", "worship",
        ),
    ),
    GenreSuggestion(
        name = "Religion",
        iconKey = "star",
        icon = Icons.Rounded.Star,
        keywords = listOf(
            "religion & spirituality", "faith", "god", "church", "spiritual",
            "prayer", "zen", "theology", "worship", "bible",
        ),
    ),
    GenreSuggestion(
        name = "Kids & Family",
        iconKey = "family",
        icon = Icons.Rounded.Face,
        keywords = listOf(
            "family", "kids", "children", "parenting", "bedtime", "childhood",
            "kids and family", "kids & family", "parent", "motherhood", "fatherhood",
            "toddlers", "nursery", "bedtime stories", "family friendly", "disney", "raising kids",
        ),
    ),
    GenreSuggestion(
        name = "Family",
        iconKey = "family",
        icon = Icons.Rounded.Face,
        keywords = listOf(
            "kids & family",
            "kids",
            "children",
            "parenting",
            "bedtime",
            "childhood",
            "family friendly",
        ),
    ),
    GenreSuggestion(
        name = "Leisure",
        iconKey = "headphones",
        icon = Icons.Rounded.Headphones,
        keywords = listOf(
            "hobbies", "relaxation", "crafts", "automotive", "aviation", "chill",
            "video games", "cars", "motorcycles", "diy", "gardening", "collecting",
            "planes", "pilots", "board games", "tabletop", "rpg", "home improvement",
            "woodworking", "pastime", "calm",
        ),
    ),
    GenreSuggestion(
        name = "Government",
        iconKey = "government",
        icon = Icons.Rounded.Gavel,
        keywords = listOf(
            "gov", "govt", "politics", "law", "policy", "court", "elections", "democracy",
            "supreme court", "legal", "constitution", "congress", "senate", "diplomacy",
            "foreign policy", "geopolitics", "white house", "legislation", "civics",
            "voting", "campaign",
        ),
    ),
    GenreSuggestion(
        name = "Govt",
        iconKey = "government",
        icon = Icons.Rounded.Gavel,
        keywords = listOf(
            "government", "politics", "law", "policy", "court", "democracy",
            "supreme court", "elections", "white house",
        ),
    ),

    // Popular tags from GenreIcons
    GenreSuggestion(
        name = "Gaming",
        iconKey = "gaming",
        icon = Icons.Rounded.SportsEsports,
        keywords = listOf(
            "game", "games", "video games", "esports", "playstation", "xbox", "nintendo",
            "pc gaming", "steam", "twitch", "rpg", "zelda", "mario", "call of duty",
            "minecraft", "indie games", "speedrun", "gamers", "gameplay", "console",
        ),
    ),
    GenreSuggestion(
        name = "Coding",
        iconKey = "code",
        icon = Icons.Rounded.Code,
        keywords = listOf(
            "code", "programming", "developer", "software", "engineering", "dev",
            "python", "kotlin", "java", "javascript", "typescript", "rust", "c++",
            "go", "git", "open source", "backend", "frontend", "fullstack", "algorithms",
            "web dev", "computer science",
        ),
    ),
    GenreSuggestion(
        name = "Finance",
        iconKey = "finance",
        icon = Icons.Rounded.AttachMoney,
        keywords = listOf(
            "money", "stocks", "crypto", "investing", "wealth", "markets", "banking",
            "economy", "bitcoin", "ethereum", "etf", "dividends", "personal finance",
            "budgeting", "retirement", "passive income", "trading", "inflation",
            "financial freedom", "real estate",
        ),
    ),
    GenreSuggestion(
        name = "Travel",
        iconKey = "travel",
        icon = Icons.Rounded.Explore,
        keywords = listOf(
            "journey", "adventure", "tourism", "vacation", "explore", "flight", "places",
            "road trip", "backpacking", "destination", "nomad", "wanderlust", "hotels",
            "airports", "culture travel", "countries", "sightseeing", "solo travel",
        ),
    ),
    GenreSuggestion(
        name = "Food",
        iconKey = "food",
        icon = Icons.Rounded.Restaurant,
        keywords = listOf(
            "cooking", "culinary", "recipes", "dining", "baking", "chef", "restaurant",
            "drinks", "cuisine", "foodie", "wine", "cocktails", "coffee", "bbq", "beer",
            "gastronomy", "kitchen", "eating", "pastry", "taste", "flavor",
        ),
    ),
    GenreSuggestion(
        name = "Nature",
        iconKey = "nature",
        icon = Icons.Rounded.Park,
        keywords = listOf(
            "environment", "wildlife", "outdoors", "animals", "earth", "climate",
            "plants", "forest", "ocean", "hiking", "conservation", "camping",
            "sustainability", "birds", "ecology", "mountains", "trees", "wilderness", "national parks",
        ),
    ),
    GenreSuggestion(
        name = "Interviews",
        iconKey = "mic",
        icon = Icons.Rounded.Mic,
        keywords = listOf(
            "talk", "conversation", "interview", "podcast", "host", "dialogue", "chat",
            "guest", "discussions", "q&a", "one on one", "deep talk", "unfiltered",
            "celebrity interviews", "fireside", "conversational",
        ),
    ),
    GenreSuggestion(
        name = "Deep Dives",
        iconKey = "bulb",
        icon = Icons.Rounded.Lightbulb,
        keywords = listOf(
            "ideas", "curiosity", "analysis", "thoughts", "explaining", "investigative",
            "in depth", "deep dive", "breakdown", "why", "how it works", "essays",
            "intellectual", "insight", "explorations",
        ),
    ),
    GenreSuggestion(
        name = "Ideas",
        iconKey = "bulb",
        icon = Icons.Rounded.Lightbulb,
        keywords = listOf(
            "curiosity", "inspiration", "innovation", "brainstorm", "future",
            "concepts", "insights", "creativity", "thought provoking", "ted", "imagination",
        ),
    ),
    GenreSuggestion(
        name = "Philosophy",
        iconKey = "chat",
        icon = Icons.Rounded.Forum,
        keywords = listOf(
            "ethics", "stoicism", "logic", "morals", "deep thinking", "epistemology",
            "existentialism", "nietzsche", "plato", "marcus aurelius", "wisdom",
            "meaning of life", "human nature", "philosopher", "thinking",
        ),
    ),
    GenreSuggestion(
        name = "Books",
        iconKey = "book",
        icon = Icons.Rounded.AutoStories,
        keywords = listOf(
            "reading", "literature", "novels", "authors", "book club", "audiobooks",
            "bestsellers", "writers", "publishing", "fiction and nonfiction", "bookworm", "literary",
        ),
    ),
    GenreSuggestion(
        name = "Psychology",
        iconKey = "health",
        icon = Icons.Rounded.Favorite,
        keywords = listOf(
            "mental health", "mind", "behavior", "brain", "therapy", "counseling",
            "cognitive", "neuroscience", "emotions", "trauma", "adhd", "psychiatry",
            "subconscious", "behavioral", "psychological", "habits",
        ),
    ),
    GenreSuggestion(
        name = "Automotive",
        iconKey = "headphones",
        icon = Icons.Rounded.Headphones,
        keywords = listOf(
            "cars", "auto", "racing", "vehicles", "f1", "motorsport",
            "electric vehicles", "ev", "tesla", "mechanics", "trucks", "supercars",
            "driving", "car culture", "automotive",
        ),
    ),
    GenreSuggestion(
        name = "Favorites",
        iconKey = "star",
        icon = Icons.Rounded.Star,
        keywords = listOf(
            "best", "starred", "top", "favorite", "saved", "loved", "picks",
            "must listen", "hall of fame", "recommended", "top rated", "essential",
        ),
    ),
    GenreSuggestion(
        name = "Trending",
        iconKey = "fire",
        icon = Icons.Rounded.Whatshot,
        keywords = listOf(
            "hot", "popular", "viral", "fire", "hype", "buzz", "latest", "hit",
            "now playing", "trending now", "top charts", "charting",
        ),
    ),
)

private fun normalizeSearchToken(token: String): String = token
    .lowercase()
    .replace("&", "and")
    .replace("-", " ")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun matchesNameExact(nameLower: String, nameNorm: String, query: String, queryNorm: String): Boolean =
    nameLower == query || nameNorm == queryNorm

private fun matchesNamePrefix(nameLower: String, nameNorm: String, query: String, queryNorm: String): Boolean =
    nameLower.startsWith(query) || nameNorm.startsWith(queryNorm)

private fun matchesNameSubstring(nameLower: String, nameNorm: String, query: String, queryNorm: String): Boolean =
    nameLower.contains(query) || nameNorm.contains(queryNorm)

private fun matchesKeywordExact(keywords: List<String>, query: String, queryNorm: String): Boolean =
    keywords.any { it.equals(query, ignoreCase = true) || normalizeSearchToken(it) == queryNorm }

private fun matchesKeywordPrefix(keywords: List<String>, query: String, queryNorm: String): Boolean =
    keywords.any { it.startsWith(query, ignoreCase = true) || normalizeSearchToken(it).startsWith(queryNorm) }

private fun matchesKeywordSubstring(keywords: List<String>, query: String, queryNorm: String): Boolean =
    keywords.any { it.contains(query, ignoreCase = true) || normalizeSearchToken(it).contains(queryNorm) }

private fun matchesAllQueryTokens(
    suggestion: GenreSuggestion,
    tokens: List<String>,
): Boolean {
    if (tokens.size <= 1) return false
    val searchCorpus = (listOf(suggestion.name) + suggestion.keywords).map { normalizeSearchToken(it) }
    return tokens.all { token ->
        searchCorpus.any { it.contains(token) }
    }
}

private fun scoreSuggestion(
    suggestion: GenreSuggestion,
    trimmed: String,
    normalizedQuery: String,
    tokens: List<String>,
): Int? {
    val nameLower = suggestion.name.lowercase()
    val nameNorm = normalizeSearchToken(suggestion.name)

    return when {
        matchesNameExact(nameLower, nameNorm, trimmed, normalizedQuery) -> 0
        matchesNamePrefix(nameLower, nameNorm, trimmed, normalizedQuery) -> 1
        matchesKeywordExact(suggestion.keywords, trimmed, normalizedQuery) -> 2
        matchesNameSubstring(nameLower, nameNorm, trimmed, normalizedQuery) -> 3
        matchesKeywordPrefix(suggestion.keywords, trimmed, normalizedQuery) -> 4
        matchesKeywordSubstring(suggestion.keywords, trimmed, normalizedQuery) -> 5
        matchesAllQueryTokens(suggestion, tokens) -> 6
        suggestion.iconKey.contains(trimmed) -> 7
        else -> null
    }
}

/**
 * Filters and ranks genre suggestions according to the user's typed search query.
 * Exact name matches rank first, followed by prefix matches, keyword matches, and substrings.
 * Handles variations in punctuation, hyphenation, and '&' vs 'and'.
 * When [query] is blank, returns the full catalog in standard order.
 */
fun filterGenreSuggestions(
    query: String,
    allSuggestions: List<GenreSuggestion> = ALL_GENRE_SUGGESTIONS,
): List<GenreSuggestion> {
    val trimmed = query.trim().lowercase()
    if (trimmed.isEmpty()) return allSuggestions
    val normalizedQuery = normalizeSearchToken(query)
    val tokens = normalizedQuery.split(" ").filter { it.length >= 2 }

    return allSuggestions
        .mapNotNull { suggestion ->
            val score = scoreSuggestion(suggestion, trimmed, normalizedQuery, tokens)
            if (score != null) suggestion to score else null
        }
        .sortedBy { it.second }
        .map { it.first }
}
