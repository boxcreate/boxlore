package cx.aswin.boxlore.feature.settings.pages

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
    val featuredChip: String,
    val ctaVerb: String,
    /** Null keeps the power cell's bolt and heart. */
    val ctaIcon: ImageVector?,
    /** Completes "… helps fund our servers", e.g. "backing a power cell" or "every snack". */
    val supportPhrase: String,
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
    featuredChip = "★ SUPREME PATRON UNIT",
    ctaVerb = "Deploy",
    ctaIcon = null,
    supportPhrase = "backing a power cell",
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
            loreDescription = "",
        )
    },
    background = skin.palette.background,
    codenameFont = skin.display,
    titleFont = skin.name,
    featuredChip = "★ LEGENDARY",
    ctaVerb = skin.ctaVerb,
    ctaIcon = skin.ctaIcon,
    supportPhrase = "every ${skin.missionNoun}",
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

/** [supportPhrase] names what a contribution backs, e.g. "backing a power cell" or "every snack". */
internal fun buildSupportMissionAnnotatedString(
    totalListeningHours: Long?,
    supportPhrase: String = ControlSupportPageStyle.supportPhrase,
): AnnotatedString =
    buildAnnotatedString {
        append("Software that helps people learn, listen, and explore should simply exist without a catch. ")
        if (totalListeningHours != null && totalListeningHours >= 5L) {
            append("If boxlore has been a great companion across your ")
            withStyle(
                SpanStyle(
                    fontWeight = GoogleSansWeight.bold,
                    color = Color.White,
                ),
            ) {
                append("$totalListeningHours hours")
            }
            append(" of listening, $supportPhrase helps keep the app free and ad-free for everyone.")
        } else {
            append("No ads, no paywalls. ${supportPhrase.replaceFirstChar { it.uppercaseChar() }} ")
            append("helps fund our servers and keep boxlore open for everyone.")
        }
    }

internal fun getSupportMissionText(
    totalListeningHours: Long?,
    supportPhrase: String = ControlSupportPageStyle.supportPhrase,
): String = buildSupportMissionAnnotatedString(totalListeningHours, supportPhrase).text
