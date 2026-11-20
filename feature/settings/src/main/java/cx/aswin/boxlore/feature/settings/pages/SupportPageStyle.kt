package cx.aswin.boxlore.feature.settings.pages

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportNoticeContent
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorld
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorldFont
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorldSkin
import cx.aswin.boxlore.feature.settings.pages.supportworlds.noticeContent
import cx.aswin.boxlore.feature.settings.pages.supportworlds.supportControlNoticeContent

/**
 * What changes between today's power-cell page and an experiment world. Every world uses the same
 * page: stage, tier info, picker, button and mission card keep the power-cell layout and spacing.
 */
internal data class SupportPageStyle(
    val world: SupportWorld,
    val tiers: List<SupportTierCardData>,
    val background: Color,
    val codenameFont: SupportWorldFont,
    /** Null keeps the app's text font for tier names. */
    val titleFont: SupportWorldFont?,
    @StringRes val featuredChip: Int,
    /** The pay button: "Deploy %1$s", where %1$s is the tier's name. */
    @StringRes val ctaVerb: Int,
    /** Null keeps the power cell's bolt and heart. */
    val ctaIcon: ImageVector?,
    /** The mission lines, which name what a contribution backs, e.g. "backing a power cell" or "every snack". */
    val mission: SupportMissionCopy,
    val cardSurface: Color,
    val notice: (index: Int, accent: Color) -> SupportNoticeContent,
    /** Backdrop, platform, particles and props of a world. Null is today's power-cell page. */
    val skin: SupportWorldSkin?,
)

private val StalinistOne = SupportWorldFont(FontFamily(Font(R.font.stalinist_one, FontWeight.Normal)), FontWeight.Normal)

internal val ControlSupportPageStyle = SupportPageStyle(
    world = SupportWorld.Control,
    tiers = SUPPORT_TIER_CARDS,
    background = Color.Black,
    codenameFont = StalinistOne,
    titleFont = null,
    featuredChip = R.string.settings_support_featured_control,
    ctaVerb = R.string.settings_support_cta_control,
    ctaIcon = null,
    mission = SupportMissionCopy(R.string.settings_support_mission_companion_control, R.string.settings_support_mission_open_control),
    cardSurface = Color(0xFF0D0D14),
    notice = { index, accent ->
        val tier = SUPPORT_TIER_CARDS[index]
        supportControlNoticeContent(tier.iconRes, accent, tier.cost)
    },
    skin = null,
)

/** The look today's power-cell page composes with. Worlds provide their own. */
internal val LocalSupportPageStyle = staticCompositionLocalOf { ControlSupportPageStyle }

/**
 * A world keeps the power cells' prices, server time, energy segments and colours, and swaps the
 * names, art, codenames, fonts and copy.
 */
internal fun supportWorldPageStyle(skin: SupportWorldSkin): SupportPageStyle = SupportPageStyle(
    world = skin.world,
    tiers = SUPPORT_TIER_CARDS.mapIndexed { index, card ->
        val words = skin.tierNames[index].uppercase().split(" ")
        card.copy(
            title = skin.tierNames[index],
            iconRes = skin.tierArt[index],
            codenameLine1 = words.first(),
            codenameLine2 = words.drop(1).joinToString(" "),
            loreDescription = null,
        )
    },
    background = skin.palette.background,
    codenameFont = skin.display,
    titleFont = skin.name,
    featuredChip = R.string.settings_support_featured_world,
    ctaVerb = skin.ctaVerb,
    ctaIcon = skin.ctaIcon,
    mission = skin.mission,
    cardSurface = skin.palette.surface,
    notice = { index, accent -> skin.noticeContent(index, accent) },
    skin = skin,
)

/**
 * Shrinks a codename line whose [measuredWidth] at [fullSize] would overflow [availableWidth].
 * The power cells always fit at full size.
 */
internal fun supportCodenameFontSize(fullSize: Float, measuredWidth: Float, availableWidth: Float): Float =
    if (measuredWidth <= availableWidth || measuredWidth <= 0f) fullSize else fullSize * availableWidth / measuredWidth

/** A world's two mission lines: one for listeners with five or more hours, one for everyone else. */
internal data class SupportMissionCopy(
    /** "If boxlore has been a great companion across your %1$s of listening, …"; %1$s is the hours. */
    @StringRes val companion: Int,
    @StringRes val open: Int,
)

/** The mission paragraph, with the listening hours in bold once there are five or more. */
internal fun buildSupportMissionAnnotatedString(
    resources: Resources,
    totalListeningHours: Long?,
    mission: SupportMissionCopy = ControlSupportPageStyle.mission,
): AnnotatedString =
    buildAnnotatedString {
        append(resources.getString(R.string.settings_support_mission_intro))
        append(" ")
        if (totalListeningHours != null && totalListeningHours >= 5L) {
            val hoursCount = totalListeningHours.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val hours = resources.getQuantityString(R.plurals.settings_support_listening_hours, hoursCount, hoursCount)
            val sentence = resources.getString(mission.companion, hours)
            val start = sentence.indexOf(hours)
            if (start < 0) {
                append(sentence)
            } else {
                append(sentence.substring(0, start))
                withStyle(SpanStyle(fontWeight = GoogleSansWeight.bold, color = Color.White)) { append(hours) }
                append(sentence.substring(start + hours.length))
            }
        } else {
            append(resources.getString(mission.open))
        }
    }

internal fun getSupportMissionText(
    resources: Resources,
    totalListeningHours: Long?,
    mission: SupportMissionCopy = ControlSupportPageStyle.mission,
): String = buildSupportMissionAnnotatedString(resources, totalListeningHours, mission).text
