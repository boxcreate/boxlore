package cx.aswin.boxlore.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsBackNavigationTest {
    @Test
    fun whenOnboarding_navigatesBackRegardlessOfPreviousDestination() {
        val actionWithPrev = resolveSettingsBackAction(
            isOnboarding = true,
            previousDestination = ProfileSettingsDestination.Appearance,
            initialPage = "account",
        )
        assertEquals(SettingsBackAction.NavigateBack, actionWithPrev)

        val actionWithoutPrev = resolveSettingsBackAction(
            isOnboarding = true,
            previousDestination = null,
            initialPage = "account",
        )
        assertEquals(SettingsBackAction.NavigateBack, actionWithoutPrev)
    }

    @Test
    fun whenNotOnboarding_withPreviousDestination_navigatesToPreviousDestination() {
        val action = resolveSettingsBackAction(
            isOnboarding = false,
            previousDestination = ProfileSettingsDestination.Appearance,
            initialPage = null,
        )
        assertEquals(
            SettingsBackAction.NavigateTo(ProfileSettingsDestination.Appearance),
            action,
        )
    }

    @Test
    fun whenNotOnboarding_noPreviousDestination_withInitialNonHubPage_navigatesBack() {
        val action = resolveSettingsBackAction(
            isOnboarding = false,
            previousDestination = null,
            initialPage = "account",
        )
        assertEquals(SettingsBackAction.NavigateBack, action)
    }

    @Test
    fun whenNotOnboarding_noPreviousDestination_withHubOrNullInitialPage_navigatesToHub() {
        val actionHub = resolveSettingsBackAction(
            isOnboarding = false,
            previousDestination = null,
            initialPage = "hub",
        )
        assertEquals(
            SettingsBackAction.NavigateTo(ProfileSettingsDestination.Hub),
            actionHub,
        )

        val actionNull = resolveSettingsBackAction(
            isOnboarding = false,
            previousDestination = null,
            initialPage = null,
        )
        assertEquals(
            SettingsBackAction.NavigateTo(ProfileSettingsDestination.Hub),
            actionNull,
        )
    }
}
