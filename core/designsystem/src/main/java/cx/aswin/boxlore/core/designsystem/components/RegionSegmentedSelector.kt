package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Single-select connected button group for content region, shared by explore
 * and settings. Delegates to [ConnectedOptionSelector].
 */
@Composable
fun RegionSegmentedSelector(
    activeRegion: String,
    onSwitchRegion: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = canonicalRegionCode(activeRegion) ?: "us"
    ConnectedOptionSelector(
        options = REGIONS,
        selected = selected,
        onSelect = onSwitchRegion,
        modifier = modifier,
    )
}

private val REGIONS =
    listOf(
        "us" to "USA",
        "in" to "India",
        "gb" to "UK",
        "fr" to "France",
    )

/** Canonical region code -> accepted alias codes (lowercase), single source of truth. */
private val REGION_ALIASES: Map<String, Set<String>> =
    mapOf(
        "us" to setOf("us"),
        "in" to setOf("in", "ind"),
        "gb" to setOf("gb", "uk"),
        "fr" to setOf("fr"),
    )

/**
 * Canonicalizes any known alias (e.g. "uk", "ind") to its region code (e.g. "gb", "in"),
 * or `null` if [code] doesn't match any known region.
 */
internal fun canonicalRegionCode(code: String): String? {
    val normalized = code.trim().lowercase()
    return REGION_ALIASES.entries.firstOrNull { (_, aliases) -> normalized in aliases }?.key
}

/** Display label for a stored region code (e.g. charts header chip). */
fun regionDisplayLabel(code: String): String {
    val canonical = canonicalRegionCode(code)
    return REGIONS.firstOrNull { (regionCode, _) -> regionCode == canonical }?.second ?: "USA"
}
