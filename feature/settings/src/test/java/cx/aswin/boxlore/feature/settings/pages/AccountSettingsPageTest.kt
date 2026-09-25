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
    fun formatRelativeSyncTime_neverSynced_returnsNeverSynced() {
        assertEquals("Never synced", formatRelativeSyncTime(0L))
        assertEquals("Never synced", formatRelativeSyncTime(-100L))
    }

    @Test
    fun formatRelativeSyncTime_futureTimestampOrClockSkew_returnsSyncedJustNow() {
        val now = 100_000L
        assertEquals("Synced just now", formatRelativeSyncTime(now + 5_000L, now = now))
    }

    @Test
    fun formatRelativeSyncTime_secondsAgo_returnsExpectedText() {
        val now = 100_000L
        // Under 30s
        assertEquals("Synced just now", formatRelativeSyncTime(now - 15_000L, now = now))
        // 30s - 59s
        assertEquals("Synced less than a minute ago", formatRelativeSyncTime(now - 45_000L, now = now))
    }

    @Test
    fun formatRelativeSyncTime_minutesAgo_returnsExpectedText() {
        val now = 10_000_000L
        // Exactly 1 minute
        assertEquals("Synced 1m ago", formatRelativeSyncTime(now - 60_000L, now = now))
        // Multiple minutes
        assertEquals("Synced 15m ago", formatRelativeSyncTime(now - 15 * 60_000L, now = now))
        assertEquals("Synced 59m ago", formatRelativeSyncTime(now - 59 * 60_000L, now = now))
    }

    @Test
    fun formatRelativeSyncTime_hoursAgo_returnsExpectedText() {
        val now = 100_000_000L
        // Exactly 1 hour
        assertEquals("Synced 1h ago", formatRelativeSyncTime(now - 3600_000L, now = now))
        // Multiple hours
        assertEquals("Synced 5h ago", formatRelativeSyncTime(now - 5 * 3600_000L, now = now))
        assertEquals("Synced 23h ago", formatRelativeSyncTime(now - 23 * 3600_000L, now = now))
    }

    @Test
    fun formatRelativeSyncTime_daysAgo_returnsExpectedText() {
        val now = 500_000_000L
        val oneDay = 24 * 3600_000L
        // Exactly 1 day
        assertEquals("Synced 1d ago", formatRelativeSyncTime(now - oneDay, now = now))
        // Multiple days
        assertEquals("Synced 4d ago", formatRelativeSyncTime(now - 4 * oneDay, now = now))
    }
}
