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

    @Test
    fun whenNotOnboarding_withSyncAndBackupsPreviousDestination_navigatesToSyncAndBackups() {
        val action = resolveSettingsBackAction(
            isOnboarding = false,
            previousDestination = ProfileSettingsDestination.SyncAndBackups,
            initialPage = null,
        )
        assertEquals(
            SettingsBackAction.NavigateTo(ProfileSettingsDestination.SyncAndBackups),
            action,
        )
    }

    @Test
    fun themeReturnsToAppearanceAndCanBeOpenedDirectly() {
        assertEquals(ProfileSettingsDestination.Theme, "theme".toSettingsDestination())
        assertEquals(
            SettingsBackAction.NavigateTo(ProfileSettingsDestination.Appearance),
            resolveSettingsBackAction(false, ProfileSettingsDestination.Appearance, null),
        )
        assertEquals(SettingsBackAction.NavigateBack, resolveSettingsBackAction(false, null, "theme"))
    }

    @Test
    fun nestedDestinationsAnimateForwardOnEntryAndBackTowardTheirParent() {
        assertEquals(true, settingsDestinationMovesForward(ProfileSettingsDestination.Appearance, ProfileSettingsDestination.CustomTheme))
        assertEquals(false, settingsDestinationMovesForward(ProfileSettingsDestination.CustomTheme, ProfileSettingsDestination.Appearance))
        assertEquals(true, settingsDestinationMovesForward(ProfileSettingsDestination.Appearance, ProfileSettingsDestination.Theme))
        assertEquals(false, settingsDestinationMovesForward(ProfileSettingsDestination.Theme, ProfileSettingsDestination.Appearance))
        assertEquals(true, settingsDestinationMovesForward(ProfileSettingsDestination.SyncAndBackups, ProfileSettingsDestination.Account))
        assertEquals(false, settingsDestinationMovesForward(ProfileSettingsDestination.Account, ProfileSettingsDestination.SyncAndBackups))
        assertEquals(false, settingsDestinationMovesForward(ProfileSettingsDestination.Appearance, ProfileSettingsDestination.Hub))
        assertEquals(false, settingsDestinationMovesForward(ProfileSettingsDestination.CustomTheme, ProfileSettingsDestination.Theme))
        assertEquals(true, settingsDestinationMovesForward(ProfileSettingsDestination.Appearance, ProfileSettingsDestination.AppearanceHome))
        assertEquals(false, settingsDestinationMovesForward(ProfileSettingsDestination.AppearanceText, ProfileSettingsDestination.Appearance))
    }

    @Test
    fun appearancePagesReturnToTheirParentAndDirectlyOpenedPagesLeaveSettings() {
        listOf(
            ProfileSettingsDestination.Theme,
            ProfileSettingsDestination.AppearanceHome,
            ProfileSettingsDestination.AppearancePlayer,
            ProfileSettingsDestination.AppearanceNavigation,
            ProfileSettingsDestination.AppearanceText,
        ).forEach { page ->
            assertEquals(
                SettingsBackAction.NavigateTo(ProfileSettingsDestination.Appearance),
                resolveSettingsBackAction(false, null, null, currentDestination = page),
            )
        }
        // The custom theme editor returns to Theme & colors, which then returns to Appearance.
        assertEquals(
            SettingsBackAction.NavigateTo(ProfileSettingsDestination.Theme),
            resolveSettingsBackAction(false, null, null, currentDestination = ProfileSettingsDestination.CustomTheme),
        )
        // Opened straight from a link, Theme & colors leaves Settings; opened from Appearance it returns there.
        assertEquals(SettingsBackAction.NavigateBack, resolveSettingsBackAction(false, null, "theme", currentDestination = ProfileSettingsDestination.Theme))
        assertEquals(
            SettingsBackAction.NavigateTo(ProfileSettingsDestination.Appearance),
            resolveSettingsBackAction(false, null, "appearance", currentDestination = ProfileSettingsDestination.Theme),
        )
        assertEquals(SettingsBackAction.NavigateBack, resolveSettingsBackAction(false, null, "appearance", currentDestination = ProfileSettingsDestination.Appearance))
    }

    @Test
    fun toSettingsDestination_resolvesSyncAndBackupsVariants() {
        assertEquals(ProfileSettingsDestination.SyncAndBackups, "sync".toSettingsDestination())
        assertEquals(ProfileSettingsDestination.SyncAndBackups, "sync_and_backups".toSettingsDestination())
        assertEquals(ProfileSettingsDestination.SyncAndBackups, "sync-and-backups".toSettingsDestination())
        assertEquals(ProfileSettingsDestination.SyncAndBackups, "backups".toSettingsDestination())
        assertEquals(ProfileSettingsDestination.Library, "library".toSettingsDestination())
        assertEquals(ProfileSettingsDestination.Account, "account".toSettingsDestination())
        assertEquals(ProfileSettingsDestination.Support, "support".toSettingsDestination())
        assertEquals(ProfileSettingsDestination.Support, "support_us".toSettingsDestination())
        assertEquals(ProfileSettingsDestination.Hub, "unknown".toSettingsDestination())
    }
}
