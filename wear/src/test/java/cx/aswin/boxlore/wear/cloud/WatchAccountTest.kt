package cx.aswin.boxlore.wear.cloud

import cx.aswin.boxlore.core.network.model.SyncPullRequest
import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.SyncPushResponse
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.core.network.session.PairedTokenRefusal
import cx.aswin.boxlore.core.wearlink.WatchSignInReply
import cx.aswin.boxlore.wear.data.WatchLibraryStore
import cx.aswin.boxlore.wear.model.WatchEpisode
import cx.aswin.boxlore.wear.model.WatchShow
import java.io.File
import kotlinx.coroutines.CompletableDeferred
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
        var revokes = true
        val pushes = mutableListOf<SyncPushRequest>()
        val pulls = mutableListOf<SyncPullRequest>()

        override suspend fun redeem(code: String): String? {
            redeemed += code
            return token
        }

        override suspend fun revoke(token: String): Boolean {
            revoked += token
            return revokes
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
            var changes = 0
            val account =
                LiveWatchAccount({ WatchSignInReply(WatchSignInReply.OK, code = "code-1", account = "me@example.org") }, cloud, tokens, store, { synced++ }, backgroundScope, onAccountChanged = { changes++ })

            account.signIn()
            assertEquals(WatchAccountState.SigningIn, account.state.value)
            runCurrent()

            assertEquals(listOf("code-1"), cloud.redeemed)
            assertEquals("watch-token", tokens.token)
            assertEquals(WatchAccountState.SignedIn("me@example.org"), account.state.value)
            assertTrue(store.state.value.cloudLinked)
            assertEquals(1, synced)
            assertEquals(1, changes, "the API session hears of the sign-in, to trade the token")
            assertNull(account.problem.value)
            assertEquals("watch-token", WatchPairing(tokens).accountToken())
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
            var changes = 0
            val account = LiveWatchAccount({ null }, cloud, tokens, store, {}, backgroundScope, onAccountChanged = { changes++ })
            assertEquals(WatchAccountState.SignedIn("me@example.org"), account.state.value)

            // The API can't record the sign-out yet (503): it is sent again at the next start.
            cloud.revokes = false
            account.signOut()
            runCurrent()

            assertEquals(WatchAccountState.SignedOut, account.state.value)
            assertNull(tokens.token)
            assertEquals(1, changes)
            assertEquals(listOf("t"), cloud.revoked)
            assertEquals(setOf("t"), tokens.unrevoked)
            assertFalse(store.state.value.cloudLinked)
            assertEquals(listOf("1"), store.state.value.shows.map { it.id })

            cloud.revokes = true
            LiveWatchAccount({ null }, cloud, tokens, store, {}, backgroundScope).giveUpUnrevoked()
            runCurrent()
            assertEquals(listOf("t", "t"), cloud.revoked)
            assertTrue(tokens.unrevoked.isEmpty())
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

    // Regression: signing out while a sign-in was still on its way left the watch signed in after all.
    @Test
    fun `a sign-out during a sign-in try wins, whatever the try comes back with`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val cloud = FakeCloud()
            val tokens = WatchAccountStore.InMemory()
            val store = store()
            var synced = 0
            val account =
                LiveWatchAccount(
                    askPhone = {
                        gate.await()
                        WatchSignInReply(WatchSignInReply.OK, code = "code-1", account = "me@example.org")
                    },
                    api = cloud,
                    tokens = tokens,
                    store = store,
                    onSignedIn = { synced++ },
                    scope = backgroundScope,
                )

            account.signIn()
            runCurrent()
            account.signOut()
            gate.complete(Unit)
            runCurrent()

            assertEquals(WatchAccountState.SignedOut, account.state.value)
            assertNull(tokens.token)
            assertFalse(store.state.value.cloudLinked)
            assertEquals(0, synced)
        }

    // Regression: a refusal of the old token, arriving after a fresh sign-in, signed the new session out.
    @Test
    fun `a refusal of an old token does not sign a newer session out`() =
        runTest {
            val tokens = WatchAccountStore.InMemory(token = "new-token", account = "me@example.org")
            val store = store()
            store.update { it.copy(cloudLinked = true) }
            val account = LiveWatchAccount({ null }, FakeCloud(), tokens, store, {}, backgroundScope)

            account.refused("old-token")
            assertEquals(WatchAccountState.SignedIn("me@example.org"), account.state.value)
            assertEquals("new-token", tokens.token)

            account.refused("new-token")
            assertEquals(WatchAccountState.SignedOut, account.state.value)
            assertNull(tokens.token)
        }

    // Regression: signing out rebuilt the sign-in screen, which asked the phone at once and signed the watch straight back in.
    @Test
    fun `after a sign-out the sign-in screen waits for a tap, even after a restart`() =
        runTest {
            val tokens = WatchAccountStore.InMemory(token = "t", account = "me@example.org")
            var asked = 0
            val phone: suspend () -> WatchSignInReply? = {
                asked++
                WatchSignInReply(WatchSignInReply.OK, code = "code-1", account = "me@example.org")
            }
            val account = LiveWatchAccount(phone, FakeCloud(), tokens, store(), {}, backgroundScope)

            account.signOut()
            account.signInBySelf()
            runCurrent()
            assertEquals(0, asked)
            assertEquals(WatchAccountState.SignedOut, account.state.value)
            assertNull(account.problem.value)

            LiveWatchAccount(phone, FakeCloud(), tokens, store(), {}, backgroundScope).signInBySelf()
            runCurrent()
            assertEquals(0, asked, "the wait is kept across a restart")

            account.signIn()
            runCurrent()
            assertEquals(1, asked)
            assertEquals(WatchAccountState.SignedIn("me@example.org"), account.state.value)
            assertNull(tokens.hold)
        }

    @Test
    fun `a new watch asks the phone by itself`() =
        runTest {
            var asked = 0
            val account = LiveWatchAccount({ asked++.let { null } }, FakeCloud(), WatchAccountStore.InMemory(), store(), {}, backgroundScope)

            account.signInBySelf()
            runCurrent()

            assertEquals(1, asked)
            assertEquals(SignInProblem.PhoneAway, account.problem.value)
        }

    // Regression: a trade refused for an unchecked lending phone signed the watch out, and the
    // sign-in screen asked the phone again by itself until the account's daily lendings ran out.
    @Test
    fun `a sign-in lent by an unchecked phone says to finish the check there, and waits for a tap`() =
        runTest {
            val tokens = WatchAccountStore.InMemory(token = "lent", account = "me@example.org")
            var asked = 0
            val phone: suspend () -> WatchSignInReply? = { asked++.let { null } }
            val pairing = WatchPairing(tokens)
            val account = LiveWatchAccount(phone, FakeCloud(), tokens, store(), {}, backgroundScope)
            pairing.onRefused = account::refused

            pairing.onTokenRefused("lent", PairedTokenRefusal.UnattestedLender)
            account.signInBySelf()
            runCurrent()

            assertEquals(WatchAccountState.SignedOut, account.state.value)
            assertEquals(SignInProblem.CheckOnPhone, account.problem.value)
            assertEquals(0, asked)
            assertEquals(SignInProblem.CheckOnPhone, LiveWatchAccount(phone, FakeCloud(), tokens, store(), {}, backgroundScope).problem.value, "the reason is kept across a restart")

            // A token refused for any other reason waits for a tap too, without a problem to show.
            tokens.save("other", null)
            account.refused("other", PairedTokenRefusal.Invalid)
            account.signInBySelf()
            runCurrent()
            assertEquals(0, asked)
            assertNull(account.problem.value)
        }

    @Test
    fun `a refused sync names the token it used`() =
        runTest {
            val cloud = FakeCloud()
            cloud.pullAnswer = CloudAnswer.Refused
            val refused = mutableListOf<String>()
            val sync = WatchCloudSync(cloud, WatchAccountStore.InMemory(token = "t"), store(), { null }, { null }, backgroundScope, onSignedOut = { refused += it })

            sync.syncNow()

            assertEquals(listOf("t"), refused)
        }

    // Regression: with more unknown shows than one sync looks up and every lookup failing, the sync
    // asked again at once, for ever, and the shows past the first batch were never tried.
    @Test
    fun `show lookups that all fail are not retried at once, and the shows not tried yet go first next time`() =
        runTest {
            val cloud = FakeCloud()
            val follows = (1..45).map { UserSubscriptionSyncDto(it.toString(), subscribedAt = 1L, updatedAt = 1L) }
            cloud.pullAnswer = CloudAnswer.Ok(SyncPullResponse(subscriptions = follows, syncedAt = 500L))
            val looked = mutableListOf<String>()
            val sync = WatchCloudSync(cloud, WatchAccountStore.InMemory(token = "t"), store(), { id -> looked.add(id).let { null } }, { null }, backgroundScope)
            sync.start()
            runCurrent()

            // One sync ran at start; with nothing looked up it does not ask for another straight away.
            assertEquals(1, cloud.pulls.size)
            assertEquals((1..40).map(Int::toString), looked)

            looked.clear()
            sync.syncNow()
            assertEquals((41..45).map(Int::toString), looked.take(5))
        }

    @Test
    fun `a fresh token sent with a sync answer replaces the one the watch used`() =
        runTest {
            val cloud = FakeCloud()
            cloud.pullAnswer = CloudAnswer.Ok(SyncPullResponse(syncedAt = 500L), renewedToken = "fresh-token")
            val tokens = WatchAccountStore.InMemory(token = "t", account = "me@example.org")
            val sync = WatchCloudSync(cloud, tokens, store(), { null }, { null }, backgroundScope)

            assertEquals(CloudSyncResult.Done, sync.syncNow())

            assertEquals("fresh-token", tokens.token)
            assertEquals("me@example.org", tokens.account)
        }

    @Test
    fun `a fresh token for a session the watch has left is not kept`() =
        runTest {
            val tokens = WatchAccountStore.InMemory(token = "t", account = "me@example.org")
            val cloud =
                object : WatchCloudApi by FakeCloud() {
                    override suspend fun pull(token: String, request: SyncPullRequest): CloudAnswer<SyncPullResponse> {
                        // The listener signs out while the pull is on its way.
                        tokens.clear()
                        return CloudAnswer.Ok(SyncPullResponse(syncedAt = 500L), renewedToken = "fresh-token")
                    }
                }
            val sync = WatchCloudSync(cloud, tokens, store(), { null }, { null }, backgroundScope)

            sync.syncNow()

            assertNull(tokens.token)
        }

    // Regression: a renewal arriving after a sign-out could write a live token back.
    @Test
    fun `a token is swapped only while it is still the stored one, in one step`() {
        val store = WatchAccountStore.InMemory(token = "t", account = "me@example.org")

        assertTrue(store.replace(used = "t", renewed = "fresh"))
        assertEquals("fresh", store.token)
        assertEquals("me@example.org", store.account)

        // Signed out: nothing is written back.
        store.clear()
        assertFalse(store.replace(used = "fresh", renewed = "late"))
        assertNull(store.token)

        // Signed in afresh meanwhile: the newer session's token stands.
        store.save("other", "me@example.org")
        assertFalse(store.replace(used = "fresh", renewed = "late"))
        assertEquals("other", store.token)
    }
}
