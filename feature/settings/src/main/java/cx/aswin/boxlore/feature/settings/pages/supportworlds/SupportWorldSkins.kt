package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
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
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.pages.SupportMissionCopy

internal enum class SupportStageStyle { NeonShelf, OrbitRings, RuneCircle, LightShaft, CloudBank }

internal enum class SupportParticleStyle { RisingSparks, TwinklingStars, RisingEmbers, Bubbles, Sparkles }

internal data class SupportWorldPalette(
    val background: Color,
    val content: Color,
    val muted: Color,
    val surface: Color,
    val dialogSurface: Color,
    val scrim: Color,
)

internal data class SupportWorldNotice(
    @StringRes val chip: Int,
    val chipColor: Color,
    @StringRes val title: Int,
    /** `%1$s` is the tapped offer's price. */
    @StringRes val bodyTemplate: Int,
    @StringRes val dismiss: Int,
    val badge: ImageVector,
    val badgeContainer: Color,
    val badgeContent: Color,
) {
    fun body(priceLabel: String): UiText = UiText.res(bodyTemplate, priceLabel)
}

/**
 * A face plus the single weight bundled for it, so Compose never synthesizes a weight. [overhang]
 * is how far its swashes draw past the measured text, kept clear of the screen edge.
 */
internal data class SupportWorldFont(val family: FontFamily, val weight: FontWeight, val overhang: Dp = 0.dp)

internal data class SupportWorldSkin(
    val world: SupportWorld,
    val palette: SupportWorldPalette,
    @DrawableRes val backdrop: Int,
    /** Null fills the page. Orbit pins its planet edge under the stage instead. */
    val backdropHeight: Dp?,
    val display: SupportWorldFont,
    /** Null uses the app's text font for the notice chip. */
    val label: SupportWorldFont?,
    /** Null uses the app's text font for tier names. */
    val name: SupportWorldFont?,
    val stage: SupportStageStyle,
    val particles: SupportParticleStyle,
    /** Product names: English in every language, and reported to analytics. */
    val tierNames: List<String>,
    @DrawableRes val tierArt: List<Int>,
    @DrawableRes val stageProp: Int?,
    /** The pay button: "Buy %1$s", where %1$s is the tier's name. */
    @StringRes val ctaVerb: Int,
    val ctaIcon: ImageVector,
    val mission: SupportMissionCopy,
    val notice: SupportWorldNotice,
)

private val PressStart2P = SupportWorldFont(FontFamily(Font(R.font.support_press_start_2p)), FontWeight.Normal)
private val Michroma = SupportWorldFont(FontFamily(Font(R.font.support_michroma)), FontWeight.Normal)
private val CinzelDecorative = SupportWorldFont(
    FontFamily(Font(R.font.support_cinzel_decorative_black, FontWeight.Black)),
    FontWeight.Black,
    overhang = 16.dp,
)
private val Cinzel = SupportWorldFont(FontFamily(Font(R.font.support_cinzel_bold, FontWeight.Bold)), FontWeight.Bold)

/** Bundled as a subset with Latin letters, digits and basic punctuation; it only sets codenames. */
private val RubikMicrobe = SupportWorldFont(FontFamily(Font(R.font.support_rubik_microbe)), FontWeight.Normal)
private val UncialAntiqua = SupportWorldFont(FontFamily(Font(R.font.support_uncial_antiqua)), FontWeight.Normal)

internal val NeonSkin = SupportWorldSkin(
    world = SupportWorld.Neon,
    palette = SupportWorldPalette(
        background = Color(0xFF07060D),
        content = Color(0xFFF5F2FF),
        muted = Color(0xFFB4AFC8),
        surface = Color(0xFF100E1A),
        dialogSurface = Color(0xFF14121F),
        scrim = Color(0xBD04030A),
    ),
    backdrop = R.drawable.support_neon_bg,
    backdropHeight = null,
    display = PressStart2P,
    label = PressStart2P,
    name = null,
    stage = SupportStageStyle.NeonShelf,
    particles = SupportParticleStyle.RisingSparks,
    tierNames = listOf("Battery Candy", "Bolt Soda", "Rack Bar", "Tower Juice", "Glow Jar"), // i18n-ignore
    tierArt = listOf(
        R.drawable.support_neon_t1,
        R.drawable.support_neon_t2,
        R.drawable.support_neon_t3,
        R.drawable.support_neon_t4,
        R.drawable.support_neon_t5,
    ),
    stageProp = R.drawable.support_neon_shelf,
    ctaVerb = R.string.settings_support_cta_neon,
    ctaIcon = Icons.Rounded.MonetizationOn,
    mission = SupportMissionCopy(R.string.settings_support_mission_companion_neon, R.string.settings_support_mission_open_neon),
    notice = SupportWorldNotice(
        chip = R.string.settings_support_notice_neon_chip,
        chipColor = Color(0xFFFFB347),
        title = R.string.settings_support_notice_neon_title,
        bodyTemplate = R.string.settings_support_notice_neon_body,
        dismiss = R.string.settings_support_notice_neon_dismiss,
        badge = Icons.Rounded.Paid,
        badgeContainer = Color(0xFFE7A400),
        badgeContent = Color(0xFF5A3A00),
    ),
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
    ),
    backdrop = R.drawable.support_orbit_bg,
    backdropHeight = 540.dp,
    display = Michroma,
    label = Michroma,
    name = null,
    stage = SupportStageStyle.OrbitRings,
    particles = SupportParticleStyle.TwinklingStars,
    tierNames = listOf("Scout Probe", "Relay Satellite", "Cargo Shuttle", "Ring Station", "Star Cruiser"), // i18n-ignore
    tierArt = listOf(
        R.drawable.support_orbit_t1,
        R.drawable.support_orbit_t2,
        R.drawable.support_orbit_t3,
        R.drawable.support_orbit_t4,
        R.drawable.support_orbit_t5,
    ),
    stageProp = null,
    ctaVerb = R.string.settings_support_cta_orbit,
    ctaIcon = Icons.Rounded.RocketLaunch,
    mission = SupportMissionCopy(R.string.settings_support_mission_companion_orbit, R.string.settings_support_mission_open_orbit),
    notice = SupportWorldNotice(
        chip = R.string.settings_support_notice_orbit_chip,
        chipColor = Color(0xFFFF8A8E),
        title = R.string.settings_support_notice_orbit_title,
        bodyTemplate = R.string.settings_support_notice_orbit_body,
        dismiss = R.string.settings_support_notice_orbit_dismiss,
        badge = Icons.Rounded.Pause,
        badgeContainer = Color(0xFFFF5A5F),
        badgeContent = Color.White,
    ),
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
    ),
    backdrop = R.drawable.support_alchemy_bg,
    backdropHeight = null,
    display = CinzelDecorative,
    label = Cinzel,
    name = Cinzel,
    stage = SupportStageStyle.RuneCircle,
    particles = SupportParticleStyle.RisingEmbers,
    tierNames = listOf("Azure Vial", "Verdant Draught", "Sun Crystal", "Violet Grimoire", "Phoenix Egg"), // i18n-ignore
    tierArt = listOf(
        R.drawable.support_alchemy_t1,
        R.drawable.support_alchemy_t2,
        R.drawable.support_alchemy_t3,
        R.drawable.support_alchemy_t4,
        R.drawable.support_alchemy_t5,
    ),
    stageProp = null,
    ctaVerb = R.string.settings_support_cta_alchemy,
    ctaIcon = Icons.Rounded.AutoAwesome,
    mission = SupportMissionCopy(R.string.settings_support_mission_companion_alchemy, R.string.settings_support_mission_open_alchemy),
    notice = SupportWorldNotice(
        chip = R.string.settings_support_notice_alchemy_chip,
        chipColor = Color(0xFFE9DFCD),
        title = R.string.settings_support_notice_alchemy_title,
        bodyTemplate = R.string.settings_support_notice_alchemy_body,
        dismiss = R.string.settings_support_notice_alchemy_dismiss,
        badge = Icons.Rounded.Air,
        badgeContainer = Color(0xFF3A322B),
        badgeContent = Color(0xFFE9DFCD),
    ),
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
    ),
    backdrop = R.drawable.support_abyss_bg,
    backdropHeight = null,
    display = RubikMicrobe,
    label = null,
    name = null,
    stage = SupportStageStyle.LightShaft,
    particles = SupportParticleStyle.Bubbles,
    tierNames = listOf("Lantern Jelly", "Glow Nautilus", "Amber Angler", "Violet Manta", "Crimson Leviathan"), // i18n-ignore
    tierArt = listOf(
        R.drawable.support_abyss_t1,
        R.drawable.support_abyss_t2,
        R.drawable.support_abyss_t3,
        R.drawable.support_abyss_t4,
        R.drawable.support_abyss_t5,
    ),
    stageProp = null,
    ctaVerb = R.string.settings_support_cta_abyss,
    ctaIcon = Icons.Rounded.Waves,
    mission = SupportMissionCopy(R.string.settings_support_mission_companion_abyss, R.string.settings_support_mission_open_abyss),
    notice = SupportWorldNotice(
        chip = R.string.settings_support_notice_abyss_chip,
        chipColor = Color(0xFFBDEBFA),
        title = R.string.settings_support_notice_abyss_title,
        bodyTemplate = R.string.settings_support_notice_abyss_body,
        dismiss = R.string.settings_support_notice_abyss_dismiss,
        badge = Icons.Rounded.WifiOff,
        badgeContainer = Color(0xFF1E4A5C),
        badgeContent = Color(0xFFDDF4FB),
    ),
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
    ),
    backdrop = R.drawable.support_sky_bg,
    backdropHeight = null,
    display = UncialAntiqua,
    label = UncialAntiqua,
    name = UncialAntiqua,
    stage = SupportStageStyle.CloudBank,
    particles = SupportParticleStyle.Sparkles,
    tierNames = listOf("Seedling Rock", "Meadow Isle", "Windmill Isle", "Star Observatory", "Beacon Citadel"), // i18n-ignore
    tierArt = listOf(
        R.drawable.support_sky_t1,
        R.drawable.support_sky_t2,
        R.drawable.support_sky_t3,
        R.drawable.support_sky_t4,
        R.drawable.support_sky_t5,
    ),
    stageProp = null,
    ctaVerb = R.string.settings_support_cta_sky,
    ctaIcon = Icons.Rounded.ArrowUpward,
    mission = SupportMissionCopy(R.string.settings_support_mission_companion_sky, R.string.settings_support_mission_open_sky),
    notice = SupportWorldNotice(
        chip = R.string.settings_support_notice_sky_chip,
        chipColor = Color(0xFFE4DEFA),
        title = R.string.settings_support_notice_sky_title,
        bodyTemplate = R.string.settings_support_notice_sky_body,
        dismiss = R.string.settings_support_notice_sky_dismiss,
        badge = Icons.Rounded.Lock,
        badgeContainer = Color(0xFFF4F0FF),
        badgeContent = Color(0xFF1C1838),
    ),
)

internal fun supportWorldSkin(world: SupportWorld): SupportWorldSkin? = when (world) {
    SupportWorld.Control -> null
    SupportWorld.Neon -> NeonSkin
    SupportWorld.Orbit -> OrbitSkin
    SupportWorld.Alchemy -> AlchemySkin
    SupportWorld.Abyss -> AbyssSkin
    SupportWorld.Sky -> SkySkin
}
