package cx.aswin.boxlore.feature.home.settings.pages

import cx.aswin.boxlore.core.network.RecentLoginRequiredException
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
}
