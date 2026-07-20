package cx.aswin.boxlore.fcm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FcmPayloadParserTest {
    @Test
    fun testParse_emptyPayloadResolvesDefaults() {
        val payload = emptyMap<String, String>()
        val parsed = FcmPayloadParser.parse(payload)

        assertEquals("boxlore Update", parsed.title)
        assertEquals("Check out what's new in boxlore!", parsed.body)
        assertEquals("both", parsed.type)
        assertNull(parsed.route)
        assertNull(parsed.imageUrl)
        assertEquals("default", parsed.sound)
        assertEquals("View", parsed.actionLabel)
        assertTrue(parsed.showActionInPush)
        assertTrue(parsed.showActionInApp)
        assertEquals("WHAT'S NEW", parsed.category)
    }

    @Test
    fun testParse_readsValuesFromPayload() {
        val payload =
            mapOf(
                "title" to "Custom Title",
                "body" to "Custom body content",
                "type" to "push",
                "route" to "boxlore://podcast/123",
                "image" to "https://example.com/banner.png",
                "sound" to "chime",
                "action_label" to "Listen Now",
                "show_action_in_push" to "false",
                "show_action_in_app" to "true",
                "category" to "UPDATE",
            )
        val parsed = FcmPayloadParser.parse(payload)

        assertEquals("Custom Title", parsed.title)
        assertEquals("Custom body content", parsed.body)
        assertEquals("push", parsed.type)
        assertEquals("boxlore://podcast/123", parsed.route)
        assertEquals("https://example.com/banner.png", parsed.imageUrl)
        assertEquals("chime", parsed.sound)
        assertEquals("Listen Now", parsed.actionLabel)
        assertFalse(parsed.showActionInPush)
        assertTrue(parsed.showActionInApp)
        assertEquals("UPDATE", parsed.category)
    }

    @Test
    fun testParse_readsOtherToggleStates() {
        val payload =
            mapOf(
                "show_action_in_push" to "true",
                "show_action_in_app" to "false",
            )
        val parsed = FcmPayloadParser.parse(payload)

        assertTrue(parsed.showActionInPush)
        assertFalse(parsed.showActionInApp)
        assertEquals("WHAT'S NEW", parsed.category)
    }

    @Test
    fun podcastAndEpisodeIds_preferSnakeCaseThenCamelCase() {
        assertEquals(
            "p-snake",
            FcmPayloadParser.podcastId(mapOf("podcast_id" to "p-snake", "podcastId" to "p-camel")),
        )
        assertEquals("p-camel", FcmPayloadParser.podcastId(mapOf("podcastId" to "p-camel")))
        assertEquals(
            "e-snake",
            FcmPayloadParser.episodeId(mapOf("episode_id" to "e-snake", "episodeId" to "e-camel")),
        )
        assertEquals("e-camel", FcmPayloadParser.episodeId(mapOf("episodeId" to "e-camel")))
        assertNull(FcmPayloadParser.podcastId(emptyMap()))
        assertNull(FcmPayloadParser.episodeId(emptyMap()))
    }

    @Test
    fun testParse_propagatesNotificationTypeAndIds() {
        val parsed =
            FcmPayloadParser.parse(
                mapOf(
                    "type" to "new_episode",
                    "podcast_id" to "pod-1",
                    "episodeId" to "ep-9",
                ),
            )
        assertEquals("new_episode", parsed.type)
        assertEquals("pod-1", parsed.podcastId)
        assertEquals("ep-9", parsed.episodeId)
    }
}
