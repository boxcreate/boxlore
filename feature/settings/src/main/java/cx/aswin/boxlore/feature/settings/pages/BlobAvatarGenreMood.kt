package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

/**
 * Visual themes, physical accessories, and environmental backdrops representing classic podcast genres.
 * Designed to be randomized or cycled interactively, and later bound to a user's top subscribed genre.
 */
internal enum class BlobAvatarGenreMood(
    val id: String,
    val displayName: String,
    val emoji: String,
    val keyLightColor: Color,
    val rimLightColor: Color,
    val particleType: ParticleType,
    val accessoryType: AccessoryType,
    val environmentType: EnvironmentType,
) {
    ChillMusic(
        id = "chill_music",
        displayName = "Chill Music",
        emoji = "🎧",
        keyLightColor = Color(0xFF8A5CF6), // Neon Violet
        rimLightColor = Color(0xFF00E5FF), // Electric Cyan
        particleType = ParticleType.MusicNote,
        accessoryType = AccessoryType.None,
        environmentType = EnvironmentType.LofiBokeh,
    ),
    TrueCrime(
        id = "true_crime",
        displayName = "True Crime",
        emoji = "🕵️",
        keyLightColor = Color(0xFF00E5FF), // Mystery Cyan
        rimLightColor = Color(0xFF1E40AF), // Deep Sapphire
        particleType = ParticleType.RadarPulse,
        accessoryType = AccessoryType.DetectiveFedora,
        environmentType = EnvironmentType.VenetianBlinds,
    ),
    TechSciFi(
        id = "tech_scifi",
        displayName = "Tech & Sci-Fi",
        emoji = "🤖",
        keyLightColor = Color(0xFF00F5D4), // Aqua Mint
        rimLightColor = Color(0xFF00E676), // Neon Emerald
        particleType = ParticleType.EqualizerBar,
        accessoryType = AccessoryType.CyberVisor,
        environmentType = EnvironmentType.CyberMatrix,
    ),
    ComedyTalk(
        id = "comedy_talk",
        displayName = "Comedy & Talk",
        emoji = "😂",
        keyLightColor = Color(0xFFFFB300), // Warm Amber
        rimLightColor = Color(0xFFFF4081), // Candy Coral
        particleType = ParticleType.LaughSparkle,
        accessoryType = AccessoryType.ComedianBowtie,
        environmentType = EnvironmentType.ComedyStage,
    ),
    LoreStories(
        id = "lore_stories",
        displayName = "Lore & Stories",
        emoji = "🧙",
        keyLightColor = Color(0xFFFF9E00), // Campfire Gold
        rimLightColor = Color(0xFF7C3AED), // Starry Violet
        particleType = ParticleType.StoryEmber,
        accessoryType = AccessoryType.WizardHat,
        environmentType = EnvironmentType.MysticRunes,
    ),
    NewsBriefing(
        id = "news_briefing",
        displayName = "News & Briefing",
        emoji = "🎙️",
        keyLightColor = Color(0xFFFF3366), // Studio Crimson
        rimLightColor = Color(0xFF80DEEA), // Ice Cyan
        particleType = ParticleType.BroadcastArc,
        accessoryType = AccessoryType.BroadcastBoomMic,
        environmentType = EnvironmentType.OnAirSign,
    ),
    SportsAthletics(
        id = "sports_athletics",
        displayName = "Sports & Athletics",
        emoji = "🏃",
        keyLightColor = Color(0xFFFFC107), // Stadium Amber
        rimLightColor = Color(0xFF2979FF), // Electric Blue
        particleType = ParticleType.EnergySpark,
        accessoryType = AccessoryType.AthleticSweatband,
        environmentType = EnvironmentType.StadiumLights,
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

    enum class AccessoryType {
        None,
        DetectiveFedora,
        CyberVisor,
        AthleticSweatband,
        BroadcastBoomMic,
        ComedianBowtie,
        WizardHat,
    }

    enum class EnvironmentType {
        VenetianBlinds,
        CyberMatrix,
        StadiumLights,
        OnAirSign,
        LofiBokeh,
        ComedyStage,
        MysticRunes,
    }

    companion object {
        private var lastShownMood: BlobAvatarGenreMood? = null

        fun random(random: Random = Random): BlobAvatarGenreMood {
            val all = entries
            val candidates = if (all.size > 1 && lastShownMood != null) {
                all.filter { it != lastShownMood }
            } else {
                all
            }
            val chosen = candidates[random.nextInt(candidates.size)]
            lastShownMood = chosen
            return chosen
        }

        fun next(current: BlobAvatarGenreMood): BlobAvatarGenreMood {
            val all = entries
            val nextIndex = (current.ordinal + 1) % all.size
            val nextMood = all[nextIndex]
            lastShownMood = nextMood
            return nextMood
        }
    }
}
