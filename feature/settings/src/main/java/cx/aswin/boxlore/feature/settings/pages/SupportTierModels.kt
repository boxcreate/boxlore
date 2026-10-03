package cx.aswin.boxlore.feature.settings.pages

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.feature.settings.R

internal data class SupportTierCardData(
    val title: String,
    val shortDuration: String,
    val powerImpact: String,
    val energySegments: Int,
    val cost: String,
    @DrawableRes val iconRes: Int,
    val auraColor: Color,
    val isFeatured: Boolean = false,
    val codenameLine1: String,
    val codenameLine2: String,
    val loreDescription: String,
)

internal val SUPPORT_TIER_CARDS = listOf(
    SupportTierCardData(
        title = "Micro Energy Cell",
        powerImpact = "Powers the boxlore servers for 3 hours",
        shortDuration = "3h",
        energySegments = 1,
        cost = "$0.49",
        iconRes = R.drawable.ic_tier_1_micro_cell,
        auraColor = Color(0xFF2979FF),
        codenameLine1 = "WITCH",
        codenameLine2 = "CELL",
        loreDescription = "Made when a forbidden energy core was sealed inside a rune-bound casing.",
    ),
    SupportTierCardData(
        title = "Field Battery Pack",
        powerImpact = "Powers the boxlore servers for 8 hours",
        shortDuration = "8h",
        energySegments = 2,
        cost = "$0.99",
        iconRes = R.drawable.ic_tier_2_field_battery,
        auraColor = Color(0xFF00E676),
        codenameLine1 = "GRAVE",
        codenameLine2 = "PACK",
        loreDescription = "Built from dead field units salvaged after the last machine war.",
    ),
    SupportTierCardData(
        title = "Power Station",
        powerImpact = "Powers the boxlore servers for 1 full day",
        shortDuration = "1d",
        energySegments = 3,
        cost = "$2.49",
        iconRes = R.drawable.ic_tier_3_power_station,
        auraColor = Color(0xFFFFB300),
        codenameLine1 = "SUN",
        codenameLine2 = "RELIC",
        loreDescription = "Forged around a shard torn from an ancient solar reactor.",
    ),
    SupportTierCardData(
        title = "Server Tower",
        powerImpact = "Powers the boxlore servers for 3 full days",
        shortDuration = "3d",
        energySegments = 4,
        cost = "$6.99",
        iconRes = R.drawable.ic_tier_4_server_tower,
        auraColor = Color(0xFF8B5CF6),
        codenameLine1 = "VOID",
        codenameLine2 = "SPIRE",
        loreDescription = "Created when a server tower was rebuilt to draw power from unstable dark matter.",
    ),
    SupportTierCardData(
        title = "Quantum Beacon",
        powerImpact = "Powers the boxlore servers for 1 full week",
        shortDuration = "7d",
        energySegments = 5,
        cost = "$17.99",
        iconRes = R.drawable.ic_tier_5_quantum_beacon,
        auraColor = Color(0xFFFF2D55),
        isFeatured = true,
        codenameLine1 = "BLOOD",
        codenameLine2 = "ORB",
        loreDescription = "Born from the heart of a failed orbital weapon, still pulsing with its original charge.",
    ),
)
