package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonetizationOn
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.settings.R

internal enum class SupportStageStyle { NeonShelf, OrbitRings, RuneCircle, LightShaft, CloudBank }

internal enum class SupportCodenameStyle { SlotCode, SplitName }

internal enum class SupportParticleStyle { RisingSparks, TwinklingStars, RisingEmbers, Bubbles, Sparkles }

internal data class SupportWorldPalette(
    val background: Color,
    val content: Color,
    val muted: Color,
    val surface: Color,
    val dialogSurface: Color,
    val scrim: Color,
    val tileFill: Color,
    val tileStroke: Color,
    val idleLabel: Color,
)

internal data class SupportWorldNotice(
    val chip: String,
    val chipColor: Color,
    val title: String,
    /** `{price}` is replaced with the tapped offer's price. */
    val bodyTemplate: String,
    val dismiss: String,
    val badge: ImageVector,
    val badgeContainer: Color,
    val badgeContent: Color,
) {
    fun body(priceLabel: String): String = bodyTemplate.replace("{price}", priceLabel)
}

/** A face plus the single weight bundled for it, so Compose never synthesizes a weight. */
internal data class SupportWorldFont(val family: FontFamily, val weight: FontWeight)

internal data class SupportWorldSkin(
    val world: SupportWorld,
    val palette: SupportWorldPalette,
    @DrawableRes val backdrop: Int,
    /** Null fills the page. Orbit pins its planet edge under the stage instead. */
    val backdropHeight: Dp?,
    val display: SupportWorldFont,
    /** Average capital width in em, used to fit split codenames to the stage width. */
    val displayWidthFactor: Float,
    val label: SupportWorldFont,
    /** Null uses the app's text font for tier names. */
    val name: SupportWorldFont?,
    val codename: SupportCodenameStyle,
    val stage: SupportStageStyle,
    val particles: SupportParticleStyle,
    val tierNames: List<String>,
    @DrawableRes val tierArt: List<Int>,
    @DrawableRes val stageProp: Int?,
    val levelLabel: (Int) -> String,
    val ctaVerb: String,
    val ctaIcon: ImageVector,
    val missionNoun: String,
    val notice: SupportWorldNotice,
    val tileCorner: Dp,
    val ctaCorner: Dp,
    val objectSize: Dp,
    val objectBottom: Dp,
)

private val Bungee = SupportWorldFont(FontFamily(Font(R.font.support_bungee)), FontWeight.Normal)
private val Oxanium = SupportWorldFont(
    FontFamily(Font(R.font.support_oxanium_extrabold, FontWeight.ExtraBold)),
    FontWeight.ExtraBold,
)
private val CinzelDecorative = SupportWorldFont(
    FontFamily(Font(R.font.support_cinzel_decorative_black, FontWeight.Black)),
    FontWeight.Black,
)
private val Cinzel = SupportWorldFont(FontFamily(Font(R.font.support_cinzel_bold, FontWeight.Bold)), FontWeight.Bold)
private val Fredoka = SupportWorldFont(
    FontFamily(Font(R.font.support_fredoka_semibold, FontWeight.SemiBold)),
    FontWeight.SemiBold,
)
private val Lilita = SupportWorldFont(FontFamily(Font(R.font.support_lilita_one)), FontWeight.Normal)

private const val NOT_CHARGED = "Payments aren't set up yet, so nothing was charged."

private val ROMAN = listOf("I", "II", "III", "IV", "V")

internal val NeonSkin = SupportWorldSkin(
    world = SupportWorld.Neon,
    palette = SupportWorldPalette(
        background = Color(0xFF07060D),
        content = Color(0xFFF5F2FF),
        muted = Color(0xFFB4AFC8),
        surface = Color(0xFF100E1A),
        dialogSurface = Color(0xFF14121F),
        scrim = Color(0xBD04030A),
        tileFill = Color(0x0AFFFFFF),
        tileStroke = Color(0x1AFFFFFF),
        idleLabel = Color(0xFF9C96B4),
    ),
    backdrop = R.drawable.support_neon_bg,
    backdropHeight = null,
    display = Bungee,
    displayWidthFactor = 0.74f,
    label = Bungee,
    name = null,
    codename = SupportCodenameStyle.SlotCode,
    stage = SupportStageStyle.NeonShelf,
    particles = SupportParticleStyle.RisingSparks,
    tierNames = listOf("Battery Candy", "Bolt Soda", "Rack Bar", "Tower Juice", "Glow Jar"),
    tierArt = listOf(
        R.drawable.support_neon_t1,
        R.drawable.support_neon_t2,
        R.drawable.support_neon_t3,
        R.drawable.support_neon_t4,
        R.drawable.support_neon_t5,
    ),
    stageProp = R.drawable.support_neon_shelf,
    levelLabel = { index -> "A${index + 1}" },
    ctaVerb = "Buy",
    ctaIcon = Icons.Rounded.MonetizationOn,
    missionNoun = "snack",
    notice = SupportWorldNotice(
        chip = "COIN RETURNED",
        chipColor = Color(0xFFFFB347),
        title = "The coin slot isn't connected yet",
        bodyTemplate = "Your {price} rolled straight back out. $NOT_CHARGED",
        dismiss = "Take my coin back",
        badge = Icons.Rounded.Paid,
        badgeContainer = Color(0xFFE7A400),
        badgeContent = Color(0xFF5A3A00),
    ),
    tileCorner = 16.dp,
    ctaCorner = 18.dp,
    objectSize = 210.dp,
    objectBottom = 84.dp,
)

internal val OrbitSkin = SupportWorldSkin(
    world = SupportWorld.Orbit,
    palette = SupportWorldPalette(
        background = Color(0xFF04060E),
        content = Color(0xFFEEF2FF),
        muted = Color(0xFFAEB6CF),
        surface = Color(0xFF0B1020),
        dialogSurface = Color(0xFF0E1426),
        scrim = Color(0xBD02040A),
        tileFill = Color(0x0AFFFFFF),
        tileStroke = Color(0x1AFFFFFF),
        idleLabel = Color(0xFF97A0BC),
    ),
    backdrop = R.drawable.support_orbit_bg,
    backdropHeight = 540.dp,
    display = Oxanium,
    displayWidthFactor = 0.74f,
    label = Oxanium,
    name = null,
    codename = SupportCodenameStyle.SplitName,
    stage = SupportStageStyle.OrbitRings,
    particles = SupportParticleStyle.TwinklingStars,
    tierNames = listOf("Scout Probe", "Relay Satellite", "Cargo Shuttle", "Ring Station", "Star Cruiser"),
    tierArt = listOf(
        R.drawable.support_orbit_t1,
        R.drawable.support_orbit_t2,
        R.drawable.support_orbit_t3,
        R.drawable.support_orbit_t4,
        R.drawable.support_orbit_t5,
    ),
    stageProp = null,
    levelLabel = { index -> "TIER ${index + 1}" },
    ctaVerb = "Launch",
    ctaIcon = Icons.Rounded.RocketLaunch,
    missionNoun = "launch",
    notice = SupportWorldNotice(
        chip = "LAUNCH ON HOLD",
        chipColor = Color(0xFFFF8A8E),
        title = "Mission control isn't online yet",
        bodyTemplate = "Your {price} never left the launch pad. $NOT_CHARGED",
        dismiss = "Stand down",
        badge = Icons.Rounded.Pause,
        badgeContainer = Color(0xFFFF5A5F),
        badgeContent = Color.White,
    ),
    tileCorner = 12.dp,
    ctaCorner = 14.dp,
    objectSize = 230.dp,
    objectBottom = 60.dp,
)

internal val AlchemySkin = SupportWorldSkin(
    world = SupportWorld.Alchemy,
    palette = SupportWorldPalette(
        background = Color(0xFF0B0806),
        content = Color(0xFFF6EEDF),
        muted = Color(0xFFBFB3A0),
        surface = Color(0xFF16110C),
        dialogSurface = Color(0xFF18120D),
        scrim = Color(0xBD080503),
        tileFill = Color(0x0DFFF0D2),
        tileStroke = Color(0x1FFFF0D2),
        idleLabel = Color(0xFFA89C88),
    ),
    backdrop = R.drawable.support_alchemy_bg,
    backdropHeight = null,
    display = CinzelDecorative,
    displayWidthFactor = 0.84f,
    label = Cinzel,
    name = Cinzel,
    codename = SupportCodenameStyle.SplitName,
    stage = SupportStageStyle.RuneCircle,
    particles = SupportParticleStyle.RisingEmbers,
    tierNames = listOf("Azure Vial", "Verdant Draught", "Sun Crystal", "Violet Grimoire", "Phoenix Egg"),
    tierArt = listOf(
        R.drawable.support_alchemy_t1,
        R.drawable.support_alchemy_t2,
        R.drawable.support_alchemy_t3,
        R.drawable.support_alchemy_t4,
        R.drawable.support_alchemy_t5,
    ),
    stageProp = null,
    levelLabel = { index -> "TIER ${ROMAN[index]}" },
    ctaVerb = "Conjure",
    ctaIcon = Icons.Rounded.AutoAwesome,
    missionNoun = "relic",
    notice = SupportWorldNotice(
        chip = "THE SPELL FIZZLED",
        chipColor = Color(0xFFE9DFCD),
        title = "The cauldron isn't lit yet",
        bodyTemplate = "Your {price} stayed in your purse. $NOT_CHARGED",
        dismiss = "Cork it",
        badge = Icons.Rounded.Air,
        badgeContainer = Color(0xFF3A322B),
        badgeContent = Color(0xFFE9DFCD),
    ),
    tileCorner = 16.dp,
    ctaCorner = 18.dp,
    objectSize = 220.dp,
    objectBottom = 62.dp,
)

internal val AbyssSkin = SupportWorldSkin(
    world = SupportWorld.Abyss,
    palette = SupportWorldPalette(
        background = Color(0xFF031018),
        content = Color(0xFFE8F6FA),
        muted = Color(0xFF9FB8C2),
        surface = Color(0xFF071C26),
        dialogSurface = Color(0xFF08202B),
        scrim = Color(0xBD01080C),
        tileFill = Color(0x0DC8F0FF),
        tileStroke = Color(0x1FC8F0FF),
        idleLabel = Color(0xFF8FAAB5),
    ),
    backdrop = R.drawable.support_abyss_bg,
    backdropHeight = null,
    display = Fredoka,
    displayWidthFactor = 0.66f,
    label = Fredoka,
    name = Fredoka,
    codename = SupportCodenameStyle.SplitName,
    stage = SupportStageStyle.LightShaft,
    particles = SupportParticleStyle.Bubbles,
    tierNames = listOf("Lantern Jelly", "Glow Nautilus", "Amber Angler", "Violet Manta", "Crimson Leviathan"),
    tierArt = listOf(
        R.drawable.support_abyss_t1,
        R.drawable.support_abyss_t2,
        R.drawable.support_abyss_t3,
        R.drawable.support_abyss_t4,
        R.drawable.support_abyss_t5,
    ),
    stageProp = null,
    levelLabel = { index -> "DEPTH ${index + 1}" },
    ctaVerb = "Summon",
    ctaIcon = Icons.Rounded.Waves,
    missionNoun = "creature",
    notice = SupportWorldNotice(
        chip = "NO SIGNAL",
        chipColor = Color(0xFFBDEBFA),
        title = "The deep isn't connected yet",
        bodyTemplate = "Your {price} stayed on dry land. $NOT_CHARGED",
        dismiss = "Back to the deep",
        badge = Icons.Rounded.WifiOff,
        badgeContainer = Color(0xFF1E4A5C),
        badgeContent = Color(0xFFDDF4FB),
    ),
    tileCorner = 20.dp,
    ctaCorner = 29.dp,
    objectSize = 250.dp,
    objectBottom = 50.dp,
)

internal val SkySkin = SupportWorldSkin(
    world = SupportWorld.Sky,
    palette = SupportWorldPalette(
        background = Color(0xFF15122E),
        content = Color(0xFFF4F0FF),
        muted = Color(0xFFB8B0D6),
        surface = Color(0xFF1A1633),
        dialogSurface = Color(0xFF1C1838),
        scrim = Color(0xB80A071A),
        tileFill = Color(0x0FECE2FF),
        tileStroke = Color(0x24ECE2FF),
        idleLabel = Color(0xFFA69DC8),
    ),
    backdrop = R.drawable.support_sky_bg,
    backdropHeight = null,
    display = Lilita,
    displayWidthFactor = 0.6f,
    label = Lilita,
    name = Lilita,
    codename = SupportCodenameStyle.SplitName,
    stage = SupportStageStyle.CloudBank,
    particles = SupportParticleStyle.Sparkles,
    tierNames = listOf("Seedling Rock", "Meadow Isle", "Windmill Isle", "Star Observatory", "Beacon Citadel"),
    tierArt = listOf(
        R.drawable.support_sky_t1,
        R.drawable.support_sky_t2,
        R.drawable.support_sky_t3,
        R.drawable.support_sky_t4,
        R.drawable.support_sky_t5,
    ),
    stageProp = null,
    levelLabel = { index -> "LEVEL ${index + 1}" },
    ctaVerb = "Raise",
    ctaIcon = Icons.Rounded.ArrowUpward,
    missionNoun = "island",
    notice = SupportWorldNotice(
        chip = "SKYPORT CLOSED",
        chipColor = Color(0xFFE4DEFA),
        title = "The skyport isn't open yet",
        bodyTemplate = "Your {price} stayed on the ground. $NOT_CHARGED",
        dismiss = "Back to the clouds",
        badge = Icons.Rounded.Lock,
        badgeContainer = Color(0xFFF4F0FF),
        badgeContent = Color(0xFF1C1838),
    ),
    tileCorner = 18.dp,
    ctaCorner = 20.dp,
    objectSize = 230.dp,
    objectBottom = 50.dp,
)

internal fun supportWorldSkin(world: SupportWorld): SupportWorldSkin? = when (world) {
    SupportWorld.Control -> null
    SupportWorld.Neon -> NeonSkin
    SupportWorld.Orbit -> OrbitSkin
    SupportWorld.Alchemy -> AlchemySkin
    SupportWorld.Abyss -> AbyssSkin
    SupportWorld.Sky -> SkySkin
}

/** Mirrors today's page: listeners past five hours hear about their own time with boxlore. */
internal fun supportWorldMissionText(noun: String, totalListeningHours: Long?): String =
    if (totalListeningHours != null && totalListeningHours >= 5L) {
        "If boxlore has been good company across your $totalListeningHours hours of listening, " +
            "every $noun helps keep it free and ad-free for everyone."
    } else {
        "No ads, no paywalls. Every $noun helps fund the servers and keeps boxlore open for everyone."
    }
