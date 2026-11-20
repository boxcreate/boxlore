package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.feature.settings.R

/**
 * A support tier. [title] and the codename lines are product names: they stay in English in every
 * language and are also reported to analytics, so they never change.
 */
internal data class SupportTierCardData(
    val title: String,
    @StringRes val shortDuration: Int,
    @StringRes val powerImpact: Int,
    val energySegments: Int,
    val cost: String,
    @DrawableRes val iconRes: Int,
    val auraColor: Color,
    val isFeatured: Boolean = false,
    val codenameLine1: String,
    val codenameLine2: String,
    /** Null for worlds, which show no lore line. */
    @StringRes val loreDescription: Int?,
)

internal val SUPPORT_TIER_CARDS = listOf(
    SupportTierCardData(
        title = "Micro Energy Cell", // i18n-ignore
        powerImpact = R.string.settings_support_tier1_impact,
        shortDuration = R.string.settings_support_tier1_short,
        energySegments = 1,
        cost = "$0.49",
        iconRes = R.drawable.ic_tier_1_micro_cell,
        auraColor = Color(0xFF2979FF),
        codenameLine1 = "WITCH",
        codenameLine2 = "CELL",
        loreDescription = R.string.settings_support_tier1_lore,
    ),
    SupportTierCardData(
        title = "Field Battery Pack", // i18n-ignore
        powerImpact = R.string.settings_support_tier2_impact,
        shortDuration = R.string.settings_support_tier2_short,
        energySegments = 2,
        cost = "$0.99",
        iconRes = R.drawable.ic_tier_2_field_battery,
        auraColor = Color(0xFF00E676),
        codenameLine1 = "GRAVE",
        codenameLine2 = "PACK",
        loreDescription = R.string.settings_support_tier2_lore,
    ),
    SupportTierCardData(
        title = "Power Station", // i18n-ignore
        powerImpact = R.string.settings_support_tier3_impact,
        shortDuration = R.string.settings_support_tier3_short,
        energySegments = 3,
        cost = "$2.49",
        iconRes = R.drawable.ic_tier_3_power_station,
        auraColor = Color(0xFFFFB300),
        codenameLine1 = "SUN",
        codenameLine2 = "RELIC",
        loreDescription = R.string.settings_support_tier3_lore,
    ),
    SupportTierCardData(
        title = "Server Tower", // i18n-ignore
        powerImpact = R.string.settings_support_tier4_impact,
        shortDuration = R.string.settings_support_tier4_short,
        energySegments = 4,
        cost = "$6.99",
        iconRes = R.drawable.ic_tier_4_server_tower,
        auraColor = Color(0xFF8B5CF6),
        codenameLine1 = "VOID",
        codenameLine2 = "SPIRE",
        loreDescription = R.string.settings_support_tier4_lore,
    ),
    SupportTierCardData(
        title = "Quantum Beacon", // i18n-ignore
        powerImpact = R.string.settings_support_tier5_impact,
        shortDuration = R.string.settings_support_tier5_short,
        energySegments = 5,
        cost = "$17.99",
        iconRes = R.drawable.ic_tier_5_quantum_beacon,
        auraColor = Color(0xFFFF2D55),
        isFeatured = true,
        codenameLine1 = "BLOOD",
        codenameLine2 = "ORB",
        loreDescription = R.string.settings_support_tier5_lore,
    ),
)
