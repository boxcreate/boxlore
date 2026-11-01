package cx.aswin.boxlore.core.playback

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** `first_episode_played` is claimed once per install, from the player. */
class FirstPlayClaimTest {
    private class MemoryStore(var claimed: Boolean = false) : FirstPlayStore {
        override fun isClaimed() = claimed

        override fun markClaimed() {
            claimed = true
        }
    }

    @Test
    fun `the first play claims once and later plays never do`() = runTest {
        val store = MemoryStore()
        val claim = FirstPlayClaim(store) { false }

        assertTrue(claim.claim())
        assertFalse(claim.claim())
        assertTrue(store.claimed)
    }

    @Test
    fun `plays starting together still claim once`() = runTest {
        val claim = FirstPlayClaim(MemoryStore()) { false }

        val results = List(5) { async { claim.claim() } }.awaitAll()

        assertEquals(1, results.count { it })
    }

    @Test
    fun `an updated install that already sent it from the screen stays quiet`() = runTest {
        val store = MemoryStore()
        val claim = FirstPlayClaim(store) { true }

        assertFalse(claim.claim())
        assertTrue(store.claimed)
    }

    @Test
    fun `a failed legacy read still claims`() = runTest {
        val claim = FirstPlayClaim(MemoryStore()) { error("datastore unavailable") }

        assertTrue(claim.claim())
    }
}
