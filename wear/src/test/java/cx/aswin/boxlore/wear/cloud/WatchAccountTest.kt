package cx.aswin.boxlore.wear.cloud

import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class WatchAccountTest {
    @TempDir
    lateinit var folder: File

    private val minute = 60_000L

    private class FakeCloud : WatchCloudApi {
        var token: String? = "watch-token"
        var pushAnswer: CloudAnswer<SyncPushResponse> = CloudAnswer.Ok(SyncPushResponse(syncedAt = 1L))
        var pullAnswer: CloudAnswer<SyncPullResponse> = CloudAnswer.Ok(SyncPullResponse(syncedAt = 500L))
        val redeemed = mutableListOf<String>()
        val revoked = mutableListOf<String>()
        val pushes = mutableListOf<SyncPushRequest>()
        val pulls = mutableListOf<SyncPullRequest>()

        override suspend fun redeem(code: String): String? {
            redeemed += code
            return token
        }

        override suspend fun revoke(token: String) {
            revoked += token
        }

        override suspend fun push(token: String, request: SyncPushRequest): CloudAnswer<SyncPushResponse> {
            pushes += request
            return pushAnswer
        }

        override suspend fun pull(token: String, request: SyncPullRequest): CloudAnswer<SyncPullResponse> {
            pulls += request
            return pullAnswer
        }
    }

    private fun TestScope.store() = WatchLibraryStore(File(folder, "library.json"), TestScope(StandardTestDispatcher(testScheduler)), StandardTestDispatcher(testScheduler))

    private fun episode(id: String) =
        WatchEpisode(id = id, showId = "1", title = "Episode $id", showTitle = "Show 1", artworkUrl = null, durationMinutes = 40, audioUrl = "https://example.org/$id.mp3")

    @Test
    fun `the phone's answer decides what a sign-in try leads to`() {
        assertEquals(SignInProblem.PhoneAway, WatchSignInRules.problem(null))
        assertEquals(SignInProblem.PhoneSignedOut, WatchSignInRules.problem(WatchSignInReply(WatchSignInReply.SIGNED_OUT)))
        assertEquals(SignInProblem.Failed, WatchSignInRules.problem(WatchSignInReply(WatchSignInReply.FAILED)))
        // An answer that says it worked but carries no code cannot sign anyone in.
        assertEquals(SignInProblem.Failed, WatchSignInRules.problem(WatchSignInReply(WatchSignInReply.OK)))
        assertNull(WatchSignInRules.problem(WatchSignInReply(WatchSignInReply.OK, code = "c")))
    }

    @Test
    fun `signing in redeems the phone's code, keeps the token and starts syncing`() =
        runTest {
            val cloud = FakeCloud()
            val tokens = WatchAccountStore.InMemory()
            val store = store()
            var synced = 0
            val account =
                LiveWatchAccount({ WatchSignInReply(WatchSignInReply.OK, code = "code-1", account = "me@example.org") }, cloud, tokens, store, { synced++ }, backgroundScope)

            account.signIn()
            assertEquals(WatchAccountState.SigningIn, account.state.value)
            runCurrent()

            assertEquals(listOf("code-1"), cloud.redeemed)
            assertEquals("watch-token", tokens.token)
            assertEquals(WatchAccountState.SignedIn("me@example.org"), account.state.value)
            assertTrue(store.state.value.cloudLinked)
            assertEquals(1, synced)
            assertNull(account.problem.value)
        }

    @Test
    fun `a phone that is away or signed out leaves the watch signed out and says why`() =
        runTest {
            val cloud = FakeCloud()
            val tokens = WatchAccountStore.InMemory()
            var reply: WatchSignInReply? = null
            val account = LiveWatchAccount({ reply }, cloud, tokens, store(), {}, backgroundScope)

            account.signIn()
            runCurrent()
            assertEquals(WatchAccountState.SignedOut, account.state.value)
            assertEquals(SignInProblem.PhoneAway, account.problem.value)

            reply = WatchSignInReply(WatchSignInReply.SIGNED_OUT)
            account.signIn()
            runCurrent()
            assertEquals(SignInProblem.PhoneSignedOut, account.problem.value)

            // The phone gave a code but the API would not take it.
            reply = WatchSignInReply(WatchSignInReply.OK, code = "c")
            cloud.token = null
            account.signIn()
            runCurrent()
            assertEquals(SignInProblem.Failed, account.problem.value)
            assertNull(tokens.token)
            assertTrue(cloud.redeemed == listOf("c"))
        }

    @Test
    fun `signing out gives the token up and keeps the library`() =
        runTest {
            val cloud = FakeCloud()
            val tokens = WatchAccountStore.InMemory(token = "t", account = "me@example.org")
            val store = store()
            store.update { it.copy(cloudLinked = true, shows = listOf(WatchShow("1", "Show 1", "Pub", artworkUrl = null, subscribed = true))) }
            val account = LiveWatchAccount({ null }, cloud, tokens, store, {}, backgroundScope)
            assertEquals(WatchAccountState.SignedIn("me@example.org"), account.state.value)

            account.signOut()
            runCurrent()

            assertEquals(WatchAccountState.SignedOut, account.state.value)
            assertNull(tokens.token)
            assertEquals(listOf("t"), cloud.revoked)
            assertFalse(store.state.value.cloudLinked)
            assertEquals(listOf("1"), store.state.value.shows.map { it.id })
        }

    @Test
    fun `a sync pushes what waits, then pulls from where the last pull ended`() =
        runTest {
            val cloud = FakeCloud()
            val store = store()
            store.update { it.copy(cloudLinked = true, cloudSince = 40L).progress(episode("a"), 10 * minute, 40 * minute, nowMs = 100L) }
            val looked = mutableListOf<String>()
            cloud.pullAnswer = CloudAnswer.Ok(SyncPullResponse(subscriptions = listOf(UserSubscriptionSyncDto("9", subscribedAt = 1L, updatedAt = 1L)), syncedAt = 500L))
            val sync =
                WatchCloudSync(cloud, WatchAccountStore.InMemory(token = "t"), store, { id -> looked.add(id).let { WatchShow(id, "Show $id", "Pub", null, subscribed = false) } }, { null }, backgroundScope)

            assertEquals(CloudSyncResult.Done, sync.syncNow())

            assertEquals(listOf("a"), cloud.pushes.single().history.map { it.episodeId })
            assertEquals(40L, cloud.pulls.single().since)
            assertEquals(listOf("9"), looked)
            assertEquals(listOf("9"), store.state.value.shows.map { it.id })
            assertEquals(500L, store.state.value.cloudSince)
            assertTrue(store.state.value.pendingEpisodes.isEmpty())
        }

    @Test
    fun `a watch that is not signed in does not call the cloud, and one out of reach keeps its changes`() =
        runTest {
            val cloud = FakeCloud()
            val store = store()
            store.update { it.copy(cloudLinked = true).progress(episode("a"), 10 * minute, 40 * minute, nowMs = 100L) }

            assertEquals(CloudSyncResult.NotSignedIn, WatchCloudSync(cloud, WatchAccountStore.InMemory(), store, { null }, { null }, backgroundScope).syncNow())
            assertTrue(cloud.pushes.isEmpty())

            cloud.pushAnswer = CloudAnswer.Unreachable
            val sync = WatchCloudSync(cloud, WatchAccountStore.InMemory(token = "t"), store, { null }, { null }, backgroundScope)
            assertEquals(CloudSyncResult.Unreachable, sync.syncNow())
            assertEquals(setOf("a"), store.state.value.pendingEpisodes.keys)
            // Nothing is pulled over changes that have not gone up.
            assertTrue(cloud.pulls.isEmpty())
        }

    @Test
    fun `a token the API no longer takes signs the watch out`() =
        runTest {
            val cloud = FakeCloud()
            cloud.pullAnswer = CloudAnswer.Refused
            var signedOut = 0
            val sync = WatchCloudSync(cloud, WatchAccountStore.InMemory(token = "t"), store(), { null }, { null }, backgroundScope, onSignedOut = { signedOut++ })

            assertEquals(CloudSyncResult.SignedOut, sync.syncNow())
            assertEquals(1, signedOut)
        }

    @Test
    fun `only the API's own code for a refused token signs the watch out`() {
        assertTrue(RetrofitWatchCloudApi.refusesToken(401, """{"error":"watch_token_invalid"}"""))
        // A 401 about the device's session renews by itself and says nothing about the account.
        assertFalse(RetrofitWatchCloudApi.refusesToken(401, """{"error":"session_expired"}"""))
        assertFalse(RetrofitWatchCloudApi.refusesToken(401, null))
        assertFalse(RetrofitWatchCloudApi.refusesToken(500, """{"error":"watch_token_invalid"}"""))
    }
}
