package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRequest
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingGenreSynthRequest
import cx.aswin.boxlore.core.network.model.OnboardingNextTurnRequest
import cx.aswin.boxlore.core.network.model.OnboardingNextTurnResponse
import cx.aswin.boxlore.core.network.model.OnboardingProbeDeckResponse
import cx.aswin.boxlore.core.network.model.OnboardingProbeReaction
import cx.aswin.boxlore.core.network.model.OnboardingSimilarShowsRequest
import cx.aswin.boxlore.core.network.model.toPodcast
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OnboardingGuidedContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `legacy response decodes unchanged and opt in is request specific`() {
        val response = json.decodeFromString<OnboardingNextTurnResponse>("""{"assistantMessage":"Question?","options":["Answer"],"searchSuggestion":null}""")
        assertNull(response.guided)
        assertEquals(listOf("Answer"), response.options)
        assertFalse(json.encodeToString(OnboardingNextTurnRequest(emptyList())).contains("responseFormat"))
        assertTrue(json.encodeToString(OnboardingNextTurnRequest(emptyList(), responseFormat = "guided_v1")).contains("guided_v1"))
    }

    @Test fun `structured text choices and recovery action ids decode without prose parsing`() {
        val response = json.decodeFromString<OnboardingNextTurnResponse>("""{"assistantMessage":"Readable history","options":[],"guided":{"version":1,"kind":"recovery","response":null,"question":"Tell me more?","choices":[],"actions":[{"id":"continue_preferences"}],"intent":"frustration","future":true}}""")
        assertEquals("Tell me more?", response.guided?.question)
        assertEquals("continue_preferences", response.guided?.actions?.single()?.id)
        assertNull(response.guided?.response)
    }

    @Test fun `finish hint opt in sends features and locale and decodes the hint`() {
        val legacy = json.encodeToString(OnboardingNextTurnRequest(emptyList(), responseFormat = "guided_v1"))
        listOf("features", "country", "languages").forEach { assertFalse(legacy.contains(it)) }
        val request = json.encodeToString(
            OnboardingNextTurnRequest(emptyList(), responseFormat = "guided_v1", features = listOf("finish_hint"), country = "in", languages = listOf("en", "hi")),
        )
        assertTrue(request.contains("\"features\":[\"finish_hint\"]"))
        assertTrue(request.contains("\"country\":\"in\""))
        assertTrue(request.contains("\"languages\":[\"en\",\"hi\"]"))
        val response = json.decodeFromString<OnboardingNextTurnResponse>(
            """{"assistantMessage":"Q","options":["a","b","c","d"],"guided":{"version":1,"kind":"question","question":"Q","choices":["a","b","c","d"],"actions":[],"intent":"preferences","finish":"prominent"}}""",
        )
        assertEquals("prominent", response.guided?.finish)
        assertNull(json.decodeFromString<OnboardingNextTurnResponse>("""{"assistantMessage":"Q","guided":{"version":1,"kind":"ready","intent":"max_turns"}}""").guided?.finish)
    }

    @Test fun `show deck cards decode with and without a preview and reactions are opt in`() {
        val deck = json.decodeFromString<OnboardingProbeDeckResponse>(
            """{"cards":[{"id":745392,"title":"Serial","author":"Serial Productions","artwork":"//img/serial.jpg","role":"core","hook":"A journalist re-investigates a murder.","question":"Could you binge a case like this?","whyItFits":"Unsolved cases.","previewUrl":"https://cdn/ep.mp3","previewStartSeconds":960,"previewDurationSeconds":2400,"future":1},{"id":7,"title":"Criminal","role":"related","hook":"Short crime stories.","question":"Shorter cases?"}]}""",
        )
        assertEquals(960, deck.cards.first().previewStartSeconds)
        assertNull(deck.cards.last().previewUrl)
        assertEquals("https://img/serial.jpg", deck.cards.first().toPodcast().imageUrl)
        assertEquals("745392", deck.cards.first().toPodcast().id)
        assertTrue(json.decodeFromString<OnboardingProbeDeckResponse>("{}").cards.isEmpty())

        assertFalse(json.encodeToString(OnboardingNextTurnRequest(emptyList())).contains("probeReactions"))
        val request = json.encodeToString(OnboardingNextTurnRequest(emptyList(), probeReactions = listOf(OnboardingProbeReaction(745392, "Serial", "listen"))))
        assertTrue(request.contains("\"probeReactions\":[{\"id\":745392,\"title\":\"Serial\",\"reaction\":\"listen\"}]"))
    }

    @Test fun `six row discovery is explicit on each request and legacy defaults omit it`() {
        assertFalse(json.encodeToString(OnboardingNextTurnRequest(emptyList())).contains("recommendationFormat"))
        assertFalse(json.encodeToString(OnboardingCurriculumRequest(emptyList())).contains("recommendationFormat"))
        val requests = listOf(
            json.encodeToString(OnboardingNextTurnRequest(emptyList(), recommendationFormat = "discovery_v2")),
            json.encodeToString(OnboardingCurriculumRequest(emptyList(), recommendationFormat = "discovery_v2")),
            json.encodeToString(OnboardingGenreSynthRequest(emptyList(), emptyList(), "", "", recommendationFormat = "discovery_v2")),
            json.encodeToString(OnboardingSimilarShowsRequest(emptyList(), recommendationFormat = "discovery_v2")),
        )
        requests.forEach { assertTrue(it.contains("\"recommendationFormat\":\"discovery_v2\"")) }
    }

    @Test fun `curriculum decoding preserves all six rows and server relevance order`() {
        val payload = (1..6).joinToString(prefix = "[", postfix = "]") { """{"rowTitle":"Interest $it","podcasts":[],"episodes":[]}""" }
        val rows = json.decodeFromString<List<OnboardingCurriculumRowDto>>(payload)
        assertEquals((1..6).map { "Interest $it" }, rows.map { it.rowTitle })
    }
}
