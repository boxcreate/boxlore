package cx.aswin.boxlore.fcm

import cx.aswin.boxlore.core.prefs.UserPreferencesRepository.Announcement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnnouncementPolicyTest {
    private fun alert() = Announcement("News", "Body", null, null, null, false, 1, "NEW RELEASE")

    @Test fun `legacy release alerts remain suppressed on Play but ordinary alerts do not`() {
        assertFalse(announcementAllowed(alert(), true, false, 28))
        assertTrue(announcementAllowed(alert().copy(releaseAlert = false), true, false, 28))
        assertTrue(announcementAllowed(alert().copy(includePlay = true), true, false, 28))
    }

    @Test fun `release version and test consent are checked independently`() {
        assertFalse(announcementAllowed(alert().copy(releaseAlert = true, releaseVersionCode = 28), false, false, 28))
        assertTrue(announcementAllowed(alert().copy(releaseVersionCode = 29), false, false, 28))
        assertFalse(announcementAllowed(alert().copy(testOnly = true, releaseAlert = false), false, false, 28))
        assertTrue(announcementAllowed(alert().copy(testOnly = true, releaseAlert = false), false, true, 28))
        assertFalse(announcementAllowed(alert().copy(releaseAlert = false), false, true, 28, isolated = true))
        assertTrue(announcementAllowed(alert().copy(testOnly = true, releaseAlert = false), false, true, 28, isolated = true))
    }

    @Test fun `isolated builds unsubscribe every production audience and Play preserves normal audiences`() {
        val isolated = announcementTopics(false, true, false)
        assertEquals(setOf("test_users"), isolated.first)
        assertTrue(isolated.second.containsAll(setOf("all_users", "prod_users", "play_users", "direct_users")))
        assertEquals(setOf("all_users", "prod_users", "play_users"), announcementTopics(false, false, true).first)
    }

    @Test fun `legacy payload defaults to compact and new payload preserves exact presentation`() {
        val old = FcmPayloadParser.parse(mapOf("title" to "News", "body" to "Exact text"))
        assertEquals("compact", old.presentation)
        val current = FcmPayloadParser.parse(mapOf("presentation" to "fullscreen", "tone" to "tertiary", "release_alert" to "false", "include_play" to "true", "test_mode" to "true"))
        assertEquals("fullscreen", current.presentation)
        assertFalse(current.releaseAlert!!)
        assertTrue(current.includePlay)
        assertTrue(current.testOnly)
    }

    @Test fun `release actions download internally and Github view preserves the announcement`() {
        val value = alert().copy(releaseAlert = true, route = "https://github.com/boxcreate/boxlore/releases/download/v29/app.apk")
        assertEquals("https://github.com/boxcreate/boxlore/releases/tag/v29", releaseAnnouncementUrl(value))
        assertTrue(isUpdaterAnnouncementAction(value, "boxlore://updates/download"))
        assertTrue(isUpdaterAnnouncementAction(value.copy(releaseAlert = null), value.route!!))
        assertFalse(shouldDismissAnnouncementForAction(value, releaseAnnouncementUrl(value)))
        assertTrue(shouldDismissAnnouncementForAction(value, "boxlore://updates/download"))
        assertEquals("https://github.com/boxcreate/boxlore/releases/tag/v29", releaseAnnouncementUrl(value.copy(releaseUrl = "https://evil.example/releases/tag/v30")))
    }
}
