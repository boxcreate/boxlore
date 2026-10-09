package cx.aswin.boxlore.feature.info.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Newspaper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubscriptionToolbarMotionTest {
    @Test
    fun `subscription confirmation retains the podcast genre instead of a generic checkmark`() {
        assertEquals(Icons.Rounded.MusicNote, subscriptionGenreIcon("Music"))
        assertEquals(Icons.Rounded.Newspaper, subscriptionGenreIcon("Daily News"))
        assertEquals(Icons.Rounded.Fingerprint, subscriptionGenreIcon("TRUE CRIME"))
    }

    @Test
    fun `missing or unknown genre retains the original heart confirmation`() {
        assertEquals(Icons.Rounded.Favorite, subscriptionGenreIcon(""))
        assertEquals(Icons.Rounded.Favorite, subscriptionGenreIcon("Uncategorized"))
    }

    @Test
    fun `opening an already subscribed show does not replay confirmation`() {
        assertEquals(SubscriptionButtonPhase.SUBSCRIBED, subscriptionButtonPhase(true, true, animationsEnabled = true))
        assertEquals(SubscriptionButtonPhase.SUBSCRIBE, subscriptionButtonPhase(false, false, animationsEnabled = true))
    }

    @Test
    fun `new subscription confirms while reversing always restores subscribe`() {
        assertEquals(SubscriptionButtonPhase.CONFIRMATION, subscriptionButtonPhase(false, true, animationsEnabled = true))
        assertEquals(SubscriptionButtonPhase.SUBSCRIBE, subscriptionButtonPhase(true, false, animationsEnabled = true))
    }

    @Test
    fun `disabled system animations settle directly to the real state`() {
        assertEquals(SubscriptionButtonPhase.SUBSCRIBED, subscriptionButtonPhase(false, true, animationsEnabled = false))
    }

    @Test
    fun `closed settings reserve no width and expose no controls`() {
        val reveal = subscriptionAutomationReveal(0f)
        assertEquals(0f, reveal.extent)
        assertEquals(0f, reveal.bellAlpha)
        assertEquals(0f, reveal.downloadAlpha)
        assertFalse(reveal.fullyVisible)
    }

    @Test
    fun `bell enters before download and partial controls remain inaccessible`() {
        val early = subscriptionAutomationReveal(0.3f)
        assertTrue(early.bellAlpha > 0f)
        assertEquals(0f, early.downloadAlpha)
        assertFalse(early.fullyVisible)
        val later = subscriptionAutomationReveal(0.7f)
        assertEquals(1f, later.bellAlpha)
        assertTrue(later.downloadAlpha > 0f)
        assertFalse(later.fullyVisible)
    }

    @Test
    fun `settled settings are fully visible and ready for interaction`() {
        val reveal = subscriptionAutomationReveal(1f)
        assertEquals(1f, reveal.extent)
        assertEquals(1f, reveal.bellAlpha)
        assertEquals(1f, reveal.downloadAlpha)
        assertTrue(reveal.fullyVisible)
    }

    @Test
    fun `invalid and out of range reveal cannot create negative or overflowing slots`() {
        assertEquals(subscriptionAutomationReveal(0f), subscriptionAutomationReveal(-0.1f))
        assertEquals(subscriptionAutomationReveal(1f), subscriptionAutomationReveal(1.1f))
        assertEquals(subscriptionAutomationReveal(0f), subscriptionAutomationReveal(Float.NaN))
    }
}
