package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * Visual themes and micro-animation styles representing classic podcast genres.
 * Designed to be randomized or cycled interactively, and later bound to a user's
 * top subscribed genre.
 */
internal enum class BlobAvatarGenreMood(
    val id: String,
    val displayName: String,
    val keyLightColor: Color,
    val rimLightColor: Color,
    val particleType: ParticleType,
) {
    ChillMusic(
        id = "chill_music",
        displayName = "Chill Music",
        keyLightColor = Color(0xFF8A5CF6), // Neon Violet
        rimLightColor = Color(0xFF00E5FF), // Electric Cyan
        particleType = ParticleType.MusicNote,
    ),
    TrueCrime(
        id = "true_crime",
        displayName = "True Crime",
        keyLightColor = Color(0xFF00E5FF), // Mystery Cyan
        rimLightColor = Color(0xFF1E40AF), // Deep Sapphire
        particleType = ParticleType.RadarPulse,
    ),
    TechSciFi(
        id = "tech_scifi",
        displayName = "Tech & Sci-Fi",
        keyLightColor = Color(0xFF00F5D4), // Aqua Mint
        rimLightColor = Color(0xFF00E676), // Neon Emerald
        particleType = ParticleType.EqualizerBar,
    ),
    ComedyTalk(
        id = "comedy_talk",
        displayName = "Comedy & Talk",
        keyLightColor = Color(0xFFFFB300), // Warm Amber
        rimLightColor = Color(0xFFFF4081), // Candy Coral
        particleType = ParticleType.LaughSparkle,
    ),
    LoreStories(
        id = "lore_stories",
        displayName = "Lore & Stories",
        keyLightColor = Color(0xFFFF9E00), // Campfire Gold
        rimLightColor = Color(0xFF7C3AED), // Starry Violet
        particleType = ParticleType.StoryEmber,
    ),
    NewsBriefing(
        id = "news_briefing",
        displayName = "News & Briefing",
        keyLightColor = Color(0xFFFF3366), // Studio Crimson
        rimLightColor = Color(0xFF80DEEA), // Ice Cyan
        particleType = ParticleType.BroadcastArc,
    ),
    SportsAthletics(
        id = "sports_athletics",
        displayName = "Sports & Athletics",
        keyLightColor = Color(0xFFFFC107), // Stadium Amber
        rimLightColor = Color(0xFF2979FF), // Electric Blue
        particleType = ParticleType.EnergySpark,
    );

    enum class ParticleType {
        MusicNote,
        RadarPulse,
        EqualizerBar,
        LaughSparkle,
        StoryEmber,
        BroadcastArc,
        EnergySpark,
    }

    companion object {
        fun random(random: Random = Random): BlobAvatarGenreMood {
            val all = entries
            return all[random.nextInt(all.size)]
        }

        fun next(current: BlobAvatarGenreMood): BlobAvatarGenreMood {
            val all = entries
            val nextIndex = (current.ordinal + 1) % all.size
            return all[nextIndex]
        }
    }
}
