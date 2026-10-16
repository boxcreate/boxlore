package cx.aswin.boxlore.feature.settings.pages

import cx.aswin.boxlore.core.auth.RecentLoginRequiredException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSettingsPageTest {

    @Test
    fun resolveAccountDeletionOutcome_success_returnsSuccessOutcome() {
        val result = Result.success(Unit)
        val outcome = resolveAccountDeletionOutcome(result)
        assertEquals(AccountDeletionOutcome.Success, outcome)
    }

    @Test
    fun resolveAccountDeletionOutcome_recentLoginRequired_returnsReauthOutcome() {
        val directError = Result.failure<Unit>(RecentLoginRequiredException())
        val directOutcome = resolveAccountDeletionOutcome(directError)
        assertEquals(AccountDeletionOutcome.ReauthRequired, directOutcome)

        val wrappedError = Result.failure<Unit>(RuntimeException("Failed to delete", RecentLoginRequiredException()))
        val wrappedOutcome = resolveAccountDeletionOutcome(wrappedError)
        assertEquals(AccountDeletionOutcome.ReauthRequired, wrappedOutcome)

        val stringMatchError = Result.failure<Unit>(IllegalStateException("requires-recent-login"))
        val stringMatchOutcome = resolveAccountDeletionOutcome(stringMatchError)
        assertEquals(AccountDeletionOutcome.ReauthRequired, stringMatchOutcome)
    }

    @Test
    fun resolveAccountDeletionOutcome_genericFailure_returnsCleanedFailureOutcome() {
        val genericError = Result.failure<Unit>(RuntimeException("Something went wrong"))
        val outcome = resolveAccountDeletionOutcome(genericError)
        assertTrue(outcome is AccountDeletionOutcome.Failure)
        assertEquals("Something went wrong", (outcome as AccountDeletionOutcome.Failure).message)
    }

    @Test
    fun resolveAccountDeletionOutcome_nullResult_returnsUnknownFailure() {
        val outcome = resolveAccountDeletionOutcome(null)
        assertTrue(outcome is AccountDeletionOutcome.Failure)
        assertEquals("An unexpected error occurred", (outcome as AccountDeletionOutcome.Failure).message)
    }

    @Test
    fun formatRelativeSyncTime_neverSynced_saysNotYet() {
        assertEquals("Not synced yet", formatRelativeSyncTime(0L))
        assertEquals("Not synced yet", formatRelativeSyncTime(-100L))
    }

    @Test
    fun formatRelativeSyncTime_futureTimestampOrClockSkew_saysJustNow() {
        val now = 100_000L
        assertEquals("Synced just now", formatRelativeSyncTime(now + 5_000L, now = now))
    }

    @Test
    fun formatRelativeSyncTime_underAMinute_saysJustNow() {
        val now = 100_000L
        assertEquals("Synced just now", formatRelativeSyncTime(now - 15_000L, now = now))
        assertEquals("Synced just now", formatRelativeSyncTime(now - 59_000L, now = now))
    }

    @Test
    fun formatRelativeSyncTime_minutesAgo() {
        val now = 10_000_000L
        assertEquals("Synced 1 min ago", formatRelativeSyncTime(now - 60_000L, now = now))
        assertEquals("Synced 15 min ago", formatRelativeSyncTime(now - 15 * 60_000L, now = now))
        assertEquals("Synced 59 min ago", formatRelativeSyncTime(now - 59 * 60_000L, now = now))
    }

    @Test
    fun formatRelativeSyncTime_hoursAgo() {
        val now = 100_000_000L
        assertEquals("Synced 1 hr ago", formatRelativeSyncTime(now - 3600_000L, now = now))
        assertEquals("Synced 23 hr ago", formatRelativeSyncTime(now - 23 * 3600_000L, now = now))
    }

    @Test
    fun formatRelativeSyncTime_daysAgo() {
        val now = 500_000_000L
        val oneDay = 24 * 3600_000L
        assertEquals("Synced yesterday", formatRelativeSyncTime(now - oneDay, now = now))
        assertEquals("Synced 4 days ago", formatRelativeSyncTime(now - 4 * oneDay, now = now))
    }

    @Test
    fun boxLoreUser_providerId_differentiatesGoogleAndPasswordAccounts() {
        val googleUser = cx.aswin.boxlore.core.model.BoxLoreUser(
            uid = "google-uid",
            email = "user@gmail.com",
            displayName = "Google User",
            providerId = "google.com",
        )
        val passwordUser = cx.aswin.boxlore.core.model.BoxLoreUser(
            uid = "pass-uid",
            email = "user@example.com",
            displayName = null,
            providerId = "password",
        )
        val defaultUser = cx.aswin.boxlore.core.model.BoxLoreUser(
            uid = "default-uid",
            email = "default@example.com",
            displayName = null,
        )

        assertEquals("google.com", googleUser.providerId)
        assertEquals("password", passwordUser.providerId)
        org.junit.Assert.assertNull(defaultUser.providerId)
    }

    @Test
    fun resolveProviderLine_saysHowTheAccountSignsIn() {
        assertEquals("Signed in with Google", resolveProviderLine("google.com"))
        assertEquals("Signed in with an email link", resolveProviderLine("emailLink"))
        assertEquals("Signed in with a password", resolveProviderLine("password"))
        assertEquals("Signed in with a password", resolveProviderLine(null))
    }

    @Test
    fun accountEcho_holdsTheCloudHugsItWhileSyncingAndCyclesOutfits() {
        assertEquals("account-cloud", accountEchoSequence(syncing = false, outfit = 0))
        assertEquals("account-syncing", accountEchoSequence(syncing = true, outfit = 3))
        assertEquals(AccountEchoOutfits[1], accountEchoSequence(syncing = false, outfit = nextAccountEchoOutfit(0)))
        assertEquals(0, nextAccountEchoOutfit(AccountEchoOutfits.lastIndex))
        assertEquals("account-cloud", accountEchoSequence(syncing = false, outfit = AccountEchoOutfits.size))
    }

    @Test
    fun settingsScreenConfig_isOnboardingDefault_isFalse() {
        val config = cx.aswin.boxlore.feature.settings.SettingsScreenConfig(
            onBack = {},
            onResetAnalytics = {},
        )
        org.junit.Assert.assertFalse(config.isOnboarding)
    }

    @Test
    fun settingsScreenConfig_isOnboardingCustom_isPreserved() {
        val config = cx.aswin.boxlore.feature.settings.SettingsScreenConfig(
            onBack = {},
            onResetAnalytics = {},
            isOnboarding = true,
        )
        org.junit.Assert.assertTrue(config.isOnboarding)
    }

    @Test
    fun `the page mode follows sign-in, onboarding and pending emails`() {
        assertEquals(AccountPageMode.SignedInOnboarding, accountPageMode(signedIn = true, onboarding = true, awaitingVerification = false, linkSent = false))
        assertEquals(AccountPageMode.SignedInSettings, accountPageMode(signedIn = true, onboarding = false, awaitingVerification = false, linkSent = true))
        assertEquals(AccountPageMode.ConfirmEmail, accountPageMode(signedIn = false, onboarding = true, awaitingVerification = true, linkSent = true))
        assertEquals(AccountPageMode.CheckLinkEmail, accountPageMode(signedIn = false, onboarding = false, awaitingVerification = false, linkSent = true))
        assertEquals(AccountPageMode.SignedOut, accountPageMode(signedIn = false, onboarding = true, awaitingVerification = false, linkSent = false))
    }
}
