package cx.aswin.boxlore.feature.settings.pages.supportworlds

/** One look for the Support page. Each maps 1:1 to a variant of the `support-world` PostHog flag. */
internal enum class SupportWorld(val variantKey: String) {
    Control("control"),
    Neon("neon"),
    Orbit("orbit"),
    Alchemy("alchemy"),
    Abyss("abyss"),
    Sky("sky"),
    ;

    companion object {
        fun fromVariant(variant: String?): SupportWorld? {
            val key = variant?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.variantKey == key }
        }
    }
}

/** Where the shown world came from. Analysis keeps only [Experiment] exposures. */
internal enum class SupportWorldSource(val analyticsValue: String) {
    Experiment("experiment"),
    Fallback("fallback"),
    DebugOverride("debug_override"),
}

internal data class SupportWorldAssignment(
    val world: SupportWorld,
    val source: SupportWorldSource,
)

/**
 * A debug override wins, then a recognised flag variant. A missing, unknown or not-yet-loaded flag
 * shows today's page and is reported as a fallback, so it never counts as an experiment exposure.
 */
internal fun resolveSupportWorld(
    flagVariant: String?,
    debugOverride: SupportWorld?,
): SupportWorldAssignment {
    if (debugOverride != null) return SupportWorldAssignment(debugOverride, SupportWorldSource.DebugOverride)
    val world = SupportWorld.fromVariant(flagVariant)
        ?: return SupportWorldAssignment(SupportWorld.Control, SupportWorldSource.Fallback)
    return SupportWorldAssignment(world, SupportWorldSource.Experiment)
}

/**
 * A store-agnostic support offer. Worlds only skin these. When Play Billing lands, [productId] is
 * filled in and the localized store price replaces [priceLabel].
 */
internal data class SupportOffer(
    val index: Int,
    val priceLabel: String,
    val amountUsd: Double,
    val shortTime: String,
    val serverTime: String,
    val productId: String? = null,
)

internal val SUPPORT_OFFERS: List<SupportOffer> = listOf(
    SupportOffer(index = 0, priceLabel = "$0.49", amountUsd = 0.49, shortTime = "3 hrs", serverTime = "3 hours"),
    SupportOffer(index = 1, priceLabel = "$0.99", amountUsd = 0.99, shortTime = "8 hrs", serverTime = "8 hours"),
    SupportOffer(index = 2, priceLabel = "$2.49", amountUsd = 2.49, shortTime = "1 day", serverTime = "1 full day"),
    SupportOffer(index = 3, priceLabel = "$6.99", amountUsd = 6.99, shortTime = "3 days", serverTime = "3 full days"),
    SupportOffer(index = 4, priceLabel = "$17.99", amountUsd = 17.99, shortTime = "1 week", serverTime = "1 full week"),
)

/** Every world, like today's page, opens on the 1-day offer. */
internal const val SUPPORT_DEFAULT_OFFER_INDEX = 2

/** Payments are not connected yet, so every pay tap shows a notice and charges nothing. */
internal const val SUPPORT_PAYMENTS_LIVE = false
