package cx.aswin.boxlore.tv.cloud

import cx.aswin.boxlore.core.network.model.ListeningHistorySyncDto
import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.network.model.TvSignInStartResponse
import cx.aswin.boxlore.core.network.session.PairedTokenRefusal
import cx.aswin.boxlore.tv.data.TvLibraryStore
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class TvAccountTest {
    private class Tokens : TvTokenStore {
        override var token: String? = null
        override var account: String? = null
        override var unrevoked: Set<String> = emptySet()

        override fun replace(used: String, renewed: String) {
            if (token == used) token = renewed
        }
    }

    /** An API whose codes are numbered; [answers] says what each poll hears, [startAnswers] what asking for a code hears first. */
    private class FakeApi(val answers: ArrayDeque<CodeCheck>, val startAnswers: ArrayDeque<CodeStart> = ArrayDeque()) : TvCloudApi {
        var starts = 0
        var polls = 0
        var revokes = true
        val revoked = mutableListOf<String>()

        override suspend fun start(): CodeStart =
            startAnswers.removeFirstOrNull() ?: CodeStart.Code(TvSignInStartResponse(code = "CODE000${++starts}", pair = "pair", expiresInSeconds = 600, intervalSeconds = 5))

        override suspend fun poll(code: String, pair: String): CodeCheck {
            polls++
            return answers.removeFirstOrNull() ?: CodeCheck.Waiting
        }

        override suspend fun revoke(token: String): Boolean {
            revoked += token
            return revokes
        }

        override suspend fun push(token: String, request: SyncPushRequest): CloudAnswer<SyncPushResponse> = CloudAnswer.Unreachable

        override suspend fun pull(token: String, request: SyncPullRequest): CloudAnswer<SyncPullResponse> = CloudAnswer.Unreachable
    }

    @Test
    fun `the TV shows a code, signs in once a phone approves it, and signs out again`(@TempDir folder: File) {
        val scope = TestScope(StandardTestDispatcher())
        val api = FakeApi(ArrayDeque(listOf(CodeCheck.Waiting, CodeCheck.Approved("token-1", "me@example.org"))))
        val tokens = Tokens()
        val store = TvLibraryStore(File(folder, "library.json"), scope, StandardTestDispatcher(scope.testScheduler))
        var syncs = 0
        var changes = 0
        val account = LiveTvAccount(api, tokens, store, { syncs++ }, scope, clock = { scope.testScheduler.currentTime }, onAccountChanged = { changes++ })

        account.startSignIn()
        scope.runCurrent()
        assertEquals(TvSignIn.ShowingCode("CODE0001"), account.signIn.value)
        scope.advanceTimeBy(10_001)
        // The TV says which account it is signed in to.
        assertEquals(TvSignIn.SignedIn("me@example.org"), account.signIn.value)
        assertEquals("token-1", tokens.token)
        assertTrue(store.state.value.cloudLinked)
        assertEquals(1, syncs)
        assertEquals(1, changes, "the API session hears of the sign-in, to trade the token")

        // The API can't record the sign-out yet: the token is given up again at the next start.
        api.revokes = false
        account.signOut()
        scope.runCurrent()
        assertEquals(TvSignIn.SignedOut, account.signIn.value)
        assertNull(tokens.token)
        assertNull(tokens.account)
        assertFalse(store.state.value.cloudLinked)
        assertEquals(2, changes)
        assertEquals(listOf("token-1"), api.revoked)
        assertEquals(setOf("token-1"), tokens.unrevoked)

        api.revokes = true
        LiveTvAccount(api, tokens, store, {}, scope).giveUpUnrevoked()
        scope.runCurrent()
        assertEquals(listOf("token-1", "token-1"), api.revoked)
        assertTrue(tokens.unrevoked.isEmpty())
    }

    @Test
    fun `after three codes run out unused the TV stops asking until the listener wants a new one`(@TempDir folder: File) {
        val scope = TestScope(StandardTestDispatcher())
        val api = FakeApi(ArrayDeque(List(LiveTvAccount.MAX_CODES) { CodeCheck.Expired }))
        val store = TvLibraryStore(File(folder, "library.json"), scope, StandardTestDispatcher(scope.testScheduler))
        val account = LiveTvAccount(api, Tokens(), store, {}, scope, clock = { scope.testScheduler.currentTime })

        account.startSignIn()
        scope.advanceTimeBy(60 * 60_000L)
        assertEquals(TvSignIn.CodeRanOut, account.signIn.value)
        assertEquals(LiveTvAccount.MAX_CODES, api.starts)
        assertEquals(LiveTvAccount.MAX_CODES, api.polls, "nothing is polled once no code is on screen")

        account.startSignIn()
        scope.runCurrent()
        assertEquals(TvSignIn.ShowingCode("CODE0004"), account.signIn.value)
    }

    @Test
    fun `too many asks from this address wait as long as the API says, keeping the code on screen`(@TempDir folder: File) {
        val scope = TestScope(StandardTestDispatcher())
        val api = FakeApi(ArrayDeque(listOf(CodeCheck.Wait(30), CodeCheck.Approved("token-1"))), startAnswers = ArrayDeque(listOf(CodeStart.Wait(60))))
        val store = TvLibraryStore(File(folder, "library.json"), scope, StandardTestDispatcher(scope.testScheduler))
        val account = LiveTvAccount(api, Tokens(), store, {}, scope, clock = { scope.testScheduler.currentTime })

        account.startSignIn()
        scope.advanceTimeBy(59_000)
        assertEquals(TvSignIn.Starting, account.signIn.value, "a held-back start tries again after Retry-After")
        scope.advanceTimeBy(1_001)
        assertEquals(TvSignIn.ShowingCode("CODE0001"), account.signIn.value)

        // First poll at 5 s hears 429 with 30 s: the next one goes 30 s later, not 5.
        scope.advanceTimeBy(5_000 + 29_000)
        assertEquals(1, api.polls)
        assertEquals(TvSignIn.ShowingCode("CODE0001"), account.signIn.value)
        scope.advanceTimeBy(1_001)
        assertEquals(2, api.polls)
        assertEquals(TvSignIn.SignedIn(null), account.signIn.value)
        assertEquals(1, api.starts)
    }

    @Test
    fun `a code that ran out is replaced by a fresh one, and leaving stops the waiting`(@TempDir folder: File) {
        val scope = TestScope(StandardTestDispatcher())
        val api = FakeApi(ArrayDeque(listOf(CodeCheck.Expired)))
        val store = TvLibraryStore(File(folder, "library.json"), scope, StandardTestDispatcher(scope.testScheduler))
        val account = LiveTvAccount(api, Tokens(), store, {}, scope, clock = { scope.testScheduler.currentTime })

        account.startSignIn()
        scope.advanceTimeBy(5_001)
        assertEquals(TvSignIn.ShowingCode("CODE0002"), account.signIn.value)

        account.cancelSignIn()
        scope.advanceTimeBy(60_000)
        assertEquals(TvSignIn.SignedOut, account.signIn.value)
        assertEquals(2, api.starts)
    }

    @Test
    fun `a token the API refuses signs the TV out, unless it signed in again since`(@TempDir folder: File) {
        val scope = TestScope(StandardTestDispatcher())
        val tokens = Tokens().apply {
            token = "old"
            account = "me@example.org"
        }
        val store = TvLibraryStore(File(folder, "library.json"), scope, StandardTestDispatcher(scope.testScheduler))
        val account = LiveTvAccount(FakeApi(ArrayDeque()), tokens, store, {}, scope)
        assertEquals(TvSignIn.SignedIn("me@example.org"), account.signIn.value)

        tokens.token = "new"
        account.refused("old")
        assertTrue(account.signIn.value is TvSignIn.SignedIn)
        account.refused("new")
        assertEquals(TvSignIn.SignedOut, account.signIn.value)
    }

    @Test
    fun `only a 401 naming the refused token signs the TV out`() {
        assertTrue(RetrofitTvCloudApi.refusesToken(401, """{"error":"watch_token_invalid"}"""))
        assertFalse(RetrofitTvCloudApi.refusesToken(401, """{"error":"session_expired"}"""))
        assertFalse(RetrofitTvCloudApi.refusesToken(401, null))
        assertFalse(RetrofitTvCloudApi.refusesToken(500, """{"error":"watch_token_invalid"}"""))
        assertEquals(60L, RetrofitTvCloudApi.retryAfterSeconds(null), "a 429 without Retry-After waits a minute")
        assertEquals(10L, RetrofitTvCloudApi.retryAfterSeconds(" 10 "))
    }

    @Test
    fun `a trade the API refuses signs the TV out through its pairing`(@TempDir folder: File) {
        val scope = TestScope(StandardTestDispatcher())
        val tokens = Tokens().apply { token = "lent" }
        val store = TvLibraryStore(File(folder, "library.json"), scope, StandardTestDispatcher(scope.testScheduler))
        val account = LiveTvAccount(FakeApi(ArrayDeque()), tokens, store, {}, scope)
        val pairing = TvPairing(tokens).apply { onRefused = { token, _ -> account.refused(token) } }
        assertEquals("lent", pairing.accountToken())

        pairing.onTokenRefused("lent", PairedTokenRefusal.Invalid)
        assertEquals(TvSignIn.SignedOut, account.signIn.value)
        assertNull(pairing.accountToken())
    }

    @Test
    fun `an answer from before a sign-out or account switch changes nothing`(@TempDir folder: File) {
        val scope = TestScope(StandardTestDispatcher())
        val store = TvLibraryStore(File(folder, "library.json"), scope, StandardTestDispatcher(scope.testScheduler))
        val tokens = Tokens().apply { token = "first-account" }
        val answer = CompletableDeferred<CloudAnswer<SyncPullResponse>>()
        val api =
            object : TvCloudApi by FakeApi(ArrayDeque()) {
                override suspend fun pull(token: String, request: SyncPullRequest) = answer.await()
            }
        val sync = TvCloudSync(api, tokens, store, { null }, { null }, scope)

        val result = scope.async { sync.syncNow() }
        scope.runCurrent()
        // Another account signs in while the first account's pull is on its way.
        tokens.token = "second-account"
        val row = ListeningHistorySyncDto(episodeId = "a", podcastId = "1", progressMs = 60_000, durationMs = 600_000, lastPlayedAt = 5, updatedAt = 5)
        answer.complete(CloudAnswer.Ok(SyncPullResponse(history = listOf(row), syncedAt = 99), renewedToken = null))
        scope.runCurrent()

        assertEquals(CloudSyncResult.NotSignedIn, result.getCompleted())
        assertEquals(0L, store.state.value.cloudSince)
        assertTrue(store.state.value.episodes.isEmpty())
    }
}
