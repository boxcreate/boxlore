package cx.aswin.boxlore.feature.widgets.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkTimeoutCooldownTest {
    private var now = 1_000L
    private val cooldown = ArtworkTimeoutCooldown(cooldownMs = 60_000L, nowMs = { now })
    private val cover = "https://example.com/cover.jpg"

    @Test
    fun aCoverThatTimedOutIsNotWaitedForAgainUntilTheCooldownEnds() {
        assertFalse(cooldown.isCoolingDown(cover, emptyList()))

        cooldown.recordTimeout(cover, emptyList())
        assertTrue(cooldown.isCoolingDown(cover, emptyList()))

        now += 59_999L
        assertTrue(cooldown.isCoolingDown(cover, emptyList()))

        now += 1L
        assertFalse("the next row may try it again", cooldown.isCoolingDown(cover, emptyList()))
    }

    @Test
    fun theFallbacksArePartOfTheRequestAndOtherCoversAreNotHeldBack() {
        cooldown.recordTimeout(cover, listOf("https://example.com/show.jpg"))

        assertTrue(cooldown.isCoolingDown(cover, listOf("https://example.com/show.jpg")))
        assertFalse("another fallback is another attempt", cooldown.isCoolingDown(cover, listOf("https://example.com/other.jpg")))
        assertFalse(cooldown.isCoolingDown(cover, emptyList()))
        assertFalse(cooldown.isCoolingDown("https://example.com/another.jpg", listOf("https://example.com/show.jpg")))
        assertFalse(cooldown.isCoolingDown(null, emptyList()))
    }
}
