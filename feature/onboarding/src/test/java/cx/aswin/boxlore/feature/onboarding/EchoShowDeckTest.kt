package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import cx.aswin.boxlore.core.network.model.OnboardingProbeReaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoShowDeckTest {
    private fun card(id: Long, preview: String? = "https://cdn/$id.mp3") =
        OnboardingProbeCard(id, "Show $id", hook = "Hook $id", question = "Question $id?", previewUrl = preview, previewStartSeconds = 960)

    private val ready = EchoDeckLogic.loaded(EchoDeck(EchoDeckStatus.Loading), listOf(card(1), card(2), card(3)))

    @Test fun `the deck is first asked for at the second answer, and not while one is loading`() {
        assertFalse(EchoDeckLogic.shouldRequest(EchoDeck(), answers = 1))
        assertTrue(EchoDeckLogic.shouldRequest(EchoDeck(), answers = 2))
        assertFalse(EchoDeckLogic.shouldRequest(EchoDeck(EchoDeckStatus.Loading), answers = 3))
        assertFalse(EchoDeckLogic.shouldRequest(EchoDeck(EchoDeckStatus.Done, attempts = 2, requestedAt = 3), answers = 4))
    }

    @Test fun `an empty deck is asked for once more after the next answer, never a seen one`() {
        val first = EchoDeckLogic.requested(EchoDeck(), answers = 2)
        val empty = EchoDeckLogic.loaded(first, emptyList())
        assertFalse(EchoDeckLogic.shouldRequest(empty, answers = 2))
        assertTrue(EchoDeckLogic.shouldRequest(empty, answers = 3))
        val second = EchoDeckLogic.failed(EchoDeckLogic.requested(empty, answers = 3))
        assertEquals(2, second.attempts)
        assertFalse(EchoDeckLogic.shouldRequest(second, answers = 4))
        val answered = EchoDeckLogic.react(EchoDeckLogic.react(EchoDeckLogic.loaded(first, listOf(card(1), card(2))).copy(showing = true), EchoDeckReaction.Like), EchoDeckReaction.Like)
        assertEquals(EchoDeckStatus.Done, answered.status)
        assertFalse(EchoDeckLogic.shouldRequest(answered, answers = 5))
    }

    @Test fun `fewer than two usable cards is no deck, and a late answer after a restart is ignored`() {
        val blankHook = card(2).copy(hook = " ")
        assertEquals(EchoDeck(EchoDeckStatus.Done), EchoDeckLogic.loaded(EchoDeck(EchoDeckStatus.Loading), listOf(card(1), blankHook, card(1))))
        assertEquals(EchoDeck(EchoDeckStatus.Done), EchoDeckLogic.failed(EchoDeck(EchoDeckStatus.Loading)))
        assertEquals(EchoDeck(EchoDeckStatus.Done, attempts = 1, requestedAt = 2), EchoDeckLogic.loaded(EchoDeckLogic.requested(EchoDeck(), 2), listOf(card(1))))
        assertEquals(EchoDeck(), EchoDeckLogic.loaded(EchoDeck(), listOf(card(1), card(2))))
        val five = EchoDeckLogic.loaded(EchoDeck(EchoDeckStatus.Loading), (1L..5L).map { card(it) })
        assertEquals(4, five.cards.size)
    }

    @Test fun `Continue opens a waiting deck, and each answer moves to the next card until the last`() {
        assertTrue(EchoDeckLogic.shouldShow(ready))
        assertNull(ready.current)
        var deck = ready.copy(showing = true)
        assertFalse(EchoDeckLogic.shouldShow(deck))
        assertEquals(1L, deck.current?.id)
        deck = EchoDeckLogic.react(deck, EchoDeckReaction.Like)
        deck = EchoDeckLogic.react(deck, EchoDeckReaction.Listen)
        assertEquals(3L, deck.current?.id)
        deck = EchoDeckLogic.react(deck, EchoDeckReaction.Unknown)
        assertFalse(deck.showing)
        assertEquals(EchoDeckStatus.Done, deck.status)
        assertFalse(EchoDeckLogic.shouldShow(deck))
        assertEquals(
            listOf(OnboardingProbeReaction(1, "Show 1", "like"), OnboardingProbeReaction(2, "Show 2", "listen"), OnboardingProbeReaction(3, "Show 3", "unknown")),
            EchoDeckLogic.requestReactions(deck),
        )
    }

    @Test fun `a loading deck is waited for once, and never after a wait ran out`() {
        assertTrue(EchoDeckLogic.shouldWait(EchoDeck(EchoDeckStatus.Loading)))
        assertFalse(EchoDeckLogic.shouldWait(EchoDeck(EchoDeckStatus.Loading, waitedOut = true)))
        assertFalse(EchoDeckLogic.shouldWait(ready))
        assertFalse(EchoDeckLogic.shouldWait(EchoDeck(EchoDeckStatus.Done)))
        assertFalse(EchoDeckLogic.shouldWait(EchoDeck()))
    }

    @Test fun `the deck remembers whether the answer or the feed build waits behind it`() {
        val beforeBuild = EchoDeckLogic.open(ready, EchoDeckNext.Build)
        assertEquals(EchoDeckNext.Build, beforeBuild.next)
        val done = EchoDeckLogic.react(EchoDeckLogic.react(EchoDeckLogic.react(beforeBuild, EchoDeckReaction.Like), EchoDeckReaction.Like), EchoDeckReaction.Like)
        assertFalse(done.showing)
        assertEquals(EchoDeckNext.Build, done.next)
        assertEquals(EchoDeckNext.Send, EchoDeckLogic.open(ready, EchoDeckNext.Send).next)
    }

    @Test fun `Back undoes the previous card's answer, and from the first card closes the deck to reopen later`() {
        val second = EchoDeckLogic.react(ready.copy(showing = true), EchoDeckReaction.Dislike)
        val first = EchoDeckLogic.back(second)
        assertEquals(1L, first.current?.id)
        assertNull(EchoDeckLogic.requestReactions(first))
        val closed = EchoDeckLogic.back(first)
        assertFalse(closed.showing)
        assertTrue(EchoDeckLogic.shouldShow(closed))
    }

    @Test fun `rewinding before the second answer drops an unseen deck but keeps answers already given`() {
        assertEquals(EchoDeck(), EchoDeckLogic.afterRewind(ready, answers = 1))
        assertSame(ready, EchoDeckLogic.afterRewind(ready, answers = 2))
        val answered = EchoDeckLogic.react(ready.copy(showing = true), EchoDeckReaction.Like).copy(showing = false)
        assertSame(answered, EchoDeckLogic.afterRewind(answered, answers = 1))
    }

    @Test fun `clips start where the API says and cards without an episode have none`() {
        assertEquals(EchoClip(1, "https://cdn/1.mp3", 960_000), EchoDeckLogic.clip(card(1)))
        assertNull(EchoDeckLogic.clip(card(2, preview = null)))
        assertNull(EchoDeckLogic.clip(card(3, preview = "ftp://cdn/3.mp3")))
    }

    @Test fun `clips fade in on every start, fade out at the end and then stop`() {
        assertEquals(0f, EchoClipTiming.volume(elapsed = 0, resumedAt = 0))
        assertEquals(1f, EchoClipTiming.volume(elapsed = 10_000, resumedAt = 0))
        assertEquals(0.5f, EchoClipTiming.volume(elapsed = 10_300, resumedAt = 10_000), 0.001f)
        assertTrue(EchoClipTiming.volume(EchoClipTiming.LengthMillis - 300, resumedAt = 0) < 0.25f)
        assertFalse(EchoClipTiming.finished(EchoClipTiming.LengthMillis - 1))
        assertTrue(EchoClipTiming.finished(EchoClipTiming.LengthMillis))
        assertEquals(1f, EchoClipTiming.progress(EchoClipTiming.LengthMillis * 2))
    }
}

class EchoCardPhysicsTest {
    @Test fun `a press sinks that spot away, the centre stays flat, and presses past the edge are capped`() {
        val centre = EchoCardPhysics.lean(100f, 200f, 200f, 400f)
        assertEquals(0f, centre.x, 0.001f)
        assertEquals(0f, centre.y, 0.001f)
        val topRight = EchoCardPhysics.lean(200f, 0f, 200f, 400f)
        assertEquals(EchoCardPhysics.MaxLeanDegrees, topRight.x)
        assertEquals(EchoCardPhysics.MaxLeanDegrees, topRight.y)
        assertEquals(topRight, EchoCardPhysics.lean(900f, -50f, 200f, 400f))
        assertEquals(EchoCardLean(0f, 0f), EchoCardPhysics.lean(10f, 10f, 0f, 0f))
    }

    @Test fun `idle drift stays within a few degrees and loops`() {
        (0..100).map { EchoCardPhysics.drift(it / 100f) }.forEach {
            assertTrue(kotlin.math.abs(it.x) <= 1.2f && kotlin.math.abs(it.y) <= 2.5f)
        }
        assertEquals(EchoCardPhysics.drift(0f).y, EchoCardPhysics.drift(1f).y, 0.001f)
        assertEquals(EchoCardPhysics.lift(0f), EchoCardPhysics.lift(1f), 0.001f)
    }

    @Test fun `the light moves away from the edge that turns away, and each glint flashes at its own angle`() {
        assertEquals(0.5f, EchoCardPhysics.light(EchoCardLean(0f, 0f)).x)
        assertTrue(EchoCardPhysics.light(EchoCardLean(0f, EchoCardPhysics.MaxLeanDegrees)).x < 0.5f)
        assertTrue(EchoCardPhysics.light(EchoCardLean(EchoCardPhysics.MaxLeanDegrees, 0f)).y > 0.4f)
        val still = EchoCardLean(0f, 0f)
        assertEquals(1f, echoSparkleAlpha((kotlin.math.PI / 2).toFloat(), still), 0.001f)
        assertEquals(0f, echoSparkleAlpha(-(kotlin.math.PI / 2).toFloat(), still), 0.001f)
        assertTrue(echoSparkleAlpha((kotlin.math.PI / 2).toFloat(), EchoCardLean(0f, 4f)) < 0.5f)
    }
}
