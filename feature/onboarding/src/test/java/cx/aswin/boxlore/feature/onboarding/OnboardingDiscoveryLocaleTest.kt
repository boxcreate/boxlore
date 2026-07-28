package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OnboardingDiscoveryLocaleTest {
    @Test
    fun discoveryLocale_canonicalizesUkAliasAndEnglishOnly() {
        assertEquals(
            OnboardingDiscoveryLocale(country = "gb", languages = listOf("en")),
            discoveryLocaleForRegion("uk"),
        )
    }

    @Test
    fun discoveryLocale_canonicalizesIndAliasWithHindi() {
        assertEquals(
            OnboardingDiscoveryLocale(country = "in", languages = listOf("en", "hi")),
            discoveryLocaleForRegion("ind"),
        )
    }

    @Test
    fun discoveryLocale_franceIncludesFrench() {
        assertEquals(
            OnboardingDiscoveryLocale(country = "fr", languages = listOf("en", "fr")),
            discoveryLocaleForRegion("fr"),
        )
    }

    @Test
    fun discoveryLocale_unknownFallsBackToUs() {
        assertEquals(
            OnboardingDiscoveryLocale(country = "us", languages = listOf("en")),
            discoveryLocaleForRegion("zz"),
        )
    }
}
