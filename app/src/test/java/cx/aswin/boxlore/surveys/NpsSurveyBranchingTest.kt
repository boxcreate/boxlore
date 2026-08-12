package cx.aswin.boxlore.surveys

import com.posthog.surveys.PostHogDisplayOpenQuestion
import com.posthog.surveys.PostHogDisplayRatingQuestion
import com.posthog.surveys.PostHogDisplaySurvey
import com.posthog.surveys.PostHogDisplaySurveyRatingType
import com.posthog.surveys.PostHogDisplaySurveyTextContentType
import com.posthog.surveys.PostHogNextSurveyQuestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NpsSurveyBranchingTest {
    @Test
    fun adjustNext_endsAfterDetractorPathLastQuestion() {
        val survey = sampleSurvey()
        val sdkNext = PostHogNextSurveyQuestion(questionIndex = 3, isSurveyCompleted = false)

        val adjusted =
            NpsSurveyBranching.adjustNext(
                survey = survey,
                answeredQuestionIndex = 2,
                sdkNext = sdkNext,
            )

        requireNotNull(adjusted)
        assertTrue(adjusted.isSurveyCompleted)
    }

    @Test
    fun adjustNext_endsAfterPassivePathLastQuestion() {
        val survey = sampleSurvey()
        val sdkNext = PostHogNextSurveyQuestion(questionIndex = 5, isSurveyCompleted = false)

        val adjusted =
            NpsSurveyBranching.adjustNext(
                survey = survey,
                answeredQuestionIndex = 4,
                sdkNext = sdkNext,
            )

        requireNotNull(adjusted)
        assertTrue(adjusted.isSurveyCompleted)
    }

    @Test
    fun adjustNext_keepsMidPathQuestionOpen() {
        val survey = sampleSurvey()
        val sdkNext = PostHogNextSurveyQuestion(questionIndex = 2, isSurveyCompleted = false)

        val adjusted =
            NpsSurveyBranching.adjustNext(
                survey = survey,
                answeredQuestionIndex = 1,
                sdkNext = sdkNext,
            )

        requireNotNull(adjusted)
        assertFalse(adjusted.isSurveyCompleted)
        assertEquals(2, adjusted.questionIndex)
    }

    @Test
    fun adjustNext_respectsSdkAlreadyCompleted() {
        val survey = sampleSurvey()
        val sdkNext = PostHogNextSurveyQuestion(questionIndex = 2, isSurveyCompleted = true)

        val adjusted =
            NpsSurveyBranching.adjustNext(
                survey = survey,
                answeredQuestionIndex = 2,
                sdkNext = sdkNext,
            )

        requireNotNull(adjusted)
        assertTrue(adjusted.isSurveyCompleted)
    }

    private fun sampleSurvey(): PostHogDisplaySurvey {
        val questions =
            listOf(
                PostHogDisplayRatingQuestion(
                    id = "6cfc6cdc-8556-4a87-9b4c-5f5826fc2503",
                    question = "NPS",
                    questionDescription = null,
                    questionDescriptionContentType = PostHogDisplaySurveyTextContentType.TEXT,
                    isOptional = false,
                    buttonText = null,
                    ratingType = PostHogDisplaySurveyRatingType.NUMBER,
                    scaleLowerBound = 0,
                    scaleUpperBound = 10,
                    lowerBoundLabel = "Not likely",
                    upperBoundLabel = "Very likely",
                ),
                open("b8007ccc-1903-4b95-9a1c-d9c16a7d9862", "disappointed"),
                open("163c6220-7b03-46b8-9073-f18435bb3eeb", "fix first"),
                open("7f66e852-a867-4697-b41a-20eff60cf244", "better"),
                open("bb126737-6122-47ed-b2e5-50b3b4fa0bd8", "must-have"),
                open("cffbaf8c-7e00-4b7c-b184-10ae52e4b5ae", "love most"),
                open("7ec7a537-cc62-4577-90c7-d05a49bb397a", "build next"),
            )
        return PostHogDisplaySurvey(
            id = "019f504e-2b74-0000-ba36-7554e845c7b1",
            name = "Boxlore NPS + Feature Ideas",
            questions = questions,
            appearance = null,
            startDate = null,
            endDate = null,
        )
    }

    private fun open(
        id: String,
        question: String,
    ): PostHogDisplayOpenQuestion =
        PostHogDisplayOpenQuestion(
            id = id,
            question = question,
            questionDescription = null,
            questionDescriptionContentType = PostHogDisplaySurveyTextContentType.TEXT,
            isOptional = true,
            buttonText = null,
        )
}
