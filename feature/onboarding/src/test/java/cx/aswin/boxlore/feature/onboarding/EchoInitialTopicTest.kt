package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingNextTurnRequest
import cx.aswin.boxlore.core.network.model.OnboardingPart
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoInitialTopicTest {
    @Test
    fun `opening answers are eight one-word topics that match their labels`() {
        assertEquals(
            listOf("Science", "Tech", "History", "True crime", "Comedy", "News", "Business", "Health"),
            OnboardingUiState().aiOptions,
        )
        assertTrue(EchoInitialTopic.entries.none { '|' in it.answer })
        assertEquals(EchoInitialTopic.entries.size, EchoInitialTopic.entries.map { it.icon }.distinct().size)
        assertNull(EchoInitialTopic.fromAnswer("Storyseeker | Serialized narratives"))
        assertNull(EchoInitialTopic.fromAnswer("Something else"))
    }

    @Test
    fun `selected topics are one user entry joined with the existing separator`() {
        val state = OnboardingUiState(aiSelectedOptions = linkedSetOf("True crime", "History"))
        val history = listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart(GuidedOnboardingLogic.submittedAnswer(state)))))
        val request = OnboardingNextTurnRequest(
            history,
            responseFormat = "guided_v1",
            features = listOf(GuidedOnboardingLogic.FINISH_HINT_FEATURE),
            country = "us",
            languages = listOf("en"),
        )
        assertEquals(
            Json.parseToJsonElement(
                """{"history":[{"role":"user","parts":[{"text":"True crime. History"}]}],"responseFormat":"guided_v1","features":["finish_hint"],"country":"us","languages":["en"]}""",
            ),
            Json.parseToJsonElement(Json.encodeToString(request)),
        )
    }

    @Test
    fun `written answers and later guided choices keep their original text`() {
        assertEquals("தமிழில் அறிவியல்", GuidedOnboardingLogic.submittedAnswer(OnboardingUiState(aiCustomInputText = "தமிழில் அறிவியல்")))
        assertEquals("Calm contemplation", GuidedOnboardingLogic.submittedAnswer(OnboardingUiState(aiSelectedOptions = setOf("Calm contemplation"))))
        assertEquals("", GuidedOnboardingLogic.submittedAnswer(OnboardingUiState(aiCustomInputText = " ")))
    }
}
