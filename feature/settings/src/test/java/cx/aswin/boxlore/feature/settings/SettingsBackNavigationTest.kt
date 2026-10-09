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
        assertEquals(ProfileSettingsDestination.Appearance, "theme".toSettingsDestination())
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
