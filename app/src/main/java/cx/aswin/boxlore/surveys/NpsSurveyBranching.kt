package cx.aswin.boxlore.surveys

import com.posthog.surveys.PostHogDisplaySurvey
import com.posthog.surveys.PostHogNextSurveyQuestion

/**
 * PostHog NPS survey "Boxlore NPS + Feature Ideas" branches by score into three
 * open-text paths, but open questions cannot reliably persist `branching: end` via
 * the surveys API. Without an end step, the SDK walks into the next path
 * (e.g. detractors see "What do you love most?").
 *
 * After each path's last question, force survey completion.
 */
internal object NpsSurveyBranching {
    /** Last question id in the detractor / passive / promoter open-text paths. */
    private val endAfterQuestionIds =
        setOf(
            // "What's the one thing we should fix or build first?"
            "163c6220-7b03-46b8-9073-f18435bb3eeb",
            // "What feature would make boxlore a must-have for you?"
            "bb126737-6122-47ed-b2e5-50b3b4fa0bd8",
            // "What would you love to see us build next?"
            "7ec7a537-cc62-4577-90c7-d05a49bb397a",
        )

    fun adjustNext(
        survey: PostHogDisplaySurvey,
        answeredQuestionIndex: Int,
        sdkNext: PostHogNextSurveyQuestion?,
    ): PostHogNextSurveyQuestion? {
        if (sdkNext?.isSurveyCompleted == true) return sdkNext
        val answeredId = survey.questions.getOrNull(answeredQuestionIndex)?.id ?: return sdkNext
        if (answeredId !in endAfterQuestionIds) return sdkNext
        return PostHogNextSurveyQuestion(
            questionIndex = answeredQuestionIndex,
            isSurveyCompleted = true,
        )
    }
}
