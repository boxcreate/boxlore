package cx.aswin.boxlore.core.catalog.crosspromo

import cx.aswin.boxlore.core.catalog.shownotes.ShowNotesParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What show notes count as an invitation to a named show, and what they never count. */
class CrossPromotionDescriptionTest {
    @Test
    fun `invitations and show descriptions support a name`() {
        mapOf(
            "Follow Blood Trails on Apple, Spotify or wherever you listen." to "Blood Trails",
            "Pandora's Box is a six-part true crime investigation." to "Pandora's Box",
            "A special preview of Murder & Magnolias, an all-new original podcast series." to "Murder & Magnolias",
            "Sharing an episode from our friends at the podcast Drilled." to "Drilled",
            "If you love this, you’re gonna love History’s Greatest Mysteries." to "History's Greatest Mysteries",
            "New episodes of Dear Daisy are published every other Friday." to "Dear Daisy",
        ).forEach { (text, name) -> assertTrue(CrossPromotionDescription.supports(text, name), text) }
    }

    @Test
    fun `passing mentions and longer names starting with it do not`() {
        assertFalse(CrossPromotionDescription.supports("We discuss Serial on this episode.", "Serial"))
        assertFalse(CrossPromotionDescription.supports("Listen to Serial Killers every Wednesday.", "Serial"))
        assertFalse(CrossPromotionDescription.supports("Follow us on Instagram for more Serial updates.", "Serial"))
        assertTrue(CrossPromotionDescription.mentions("We discuss SERIAL on this episode.", "Serial"))
    }

    @Test
    fun `premium previews and the host's own seasons are recognised`() {
        assertTrue(CrossPromotionDescription.premium("Unlock the full episode on Patreon."))
        assertTrue(CrossPromotionDescription.premium("A supporters-only bonus."))
        assertFalse(CrossPromotionDescription.premium("Thanks for listening."))
        assertTrue(CrossPromotionDescription.ownShow("A preview of the new season of You Must Remember This.", "You Must Remember This"))
        // Crediting the host as the makers is how promos introduce a new show.
        assertFalse(CrossPromotionDescription.ownShow("From the team behind Welcome to Night Vale comes a new audio drama.", "Welcome to Night Vale"))
    }

    @Test
    fun `notes name the show from friends and new show wording`() {
        assertEquals("Modern CTO", CrossPromotionDescription.friendsName("Enjoy a special episode from our friends at the Modern CTO podcast. Read more."))
        assertNull(CrossPromotionDescription.friendsName("Thanks to our friends for listening."))
        val newShow = ShowNotesParser.parse("And here’s my new show, Help Wanted: https://example.org/help-wanted")
        assertEquals("Help Wanted", CrossPromotionDescription.name(newShow, "Build For Tomorrow"))
        // A lowercase invitation target is a phrase, not a show.
        assertNull(CrossPromotionDescription.name(ShowNotesParser.parse("Follow our various socials for updates!"), "Host"))
    }
}
