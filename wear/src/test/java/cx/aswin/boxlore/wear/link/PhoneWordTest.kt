package cx.aswin.boxlore.wear.link

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

// Regression: Resume on a cold start decided before the watch had read that the phone was playing,
// so the episode started on the watch too and nobody was asked where to play.
@OptIn(ExperimentalCoroutinesApi::class)
class PhoneWordTest {
    @Test
    fun `a choice that depends on the phone waits until the phone's last word has been read`() =
        runTest {
            val word = PhoneWord(limitMs = 1_500L)
            var decided = false
            launch {
                word.await()
                decided = true
            }

            runCurrent()
            assertFalse(decided)

            word.heard()
            runCurrent()
            assertTrue(decided)
            assertEquals(0L, currentTime)
        }

    @Test
    fun `with no word from the phone the wait ends by itself, and a word already heard costs no wait`() =
        runTest {
            val silent = PhoneWord(limitMs = 1_500L)
            var decided = false
            launch {
                silent.await()
                decided = true
            }

            advanceTimeBy(1_499L)
            assertFalse(decided)
            advanceTimeBy(2L)
            assertTrue(decided)

            val heard = PhoneWord(limitMs = 1_500L).also { it.heard() }
            val before = currentTime
            heard.await()
            assertEquals(before, currentTime)
        }
}
