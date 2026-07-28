package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.model.ContentRegions

/** Canonical country + recommended languages for onboarding discovery API payloads. */
internal data class OnboardingDiscoveryLocale(
    val country: String,
    val languages: List<String>,
)

internal fun discoveryLocaleForRegion(rawRegion: String): OnboardingDiscoveryLocale {
    val country = ContentRegions.canonicalize(rawRegion)
    return OnboardingDiscoveryLocale(
        country = country,
        languages = ContentRegions.recommendedLanguages(country),
    )
}
