package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.prefs.AppLanguage

/**
 * Onboarding's first question when the phone suggests one of boxlore's translations: the suggested
 * language as the main choice, then every other language boxlore has ([others]).
 */
data class AppLanguageQuestion(val suggested: AppLanguage) {
    /** The other choices: English, then the remaining translations in their usual order. */
    val others: List<AppLanguage> get() = (listOf(AppLanguage.ENGLISH) + AppLanguage.translated) - suggested

    /** Every choice, the suggestion first; the greeting at the top cycles through them in this order. */
    val all: List<AppLanguage> get() = listOf(suggested) + others
}

/** Where onboarding starts and how the language question moves on, as pure state changes. */
internal object OnboardingAppLanguage {
    /** Analytics step name for the question (`onboarding_step_viewed`, `onboarding_abandoned`). */
    const val STEP_NAME = "app_language"

    /** `settings_interaction` key for the pick; Settings reports later changes as `app_language_changed`. */
    const val ANALYTICS_KEY = "onboarding_app_language_chosen"

    /**
     * The first state: the language question when onboarding isn't finished and [suggestion] (asked
     * only then) names a translation; otherwise Welcome.
     */
    fun initialState(onboardingCompleted: Boolean, suggestion: () -> AppLanguage?): OnboardingUiState {
        if (onboardingCompleted) return OnboardingUiState()
        val suggested = suggestion() ?: return OnboardingUiState()
        return OnboardingUiState(currentStep = OnboardingStep.APP_LANGUAGE, appLanguageQuestion = AppLanguageQuestion(suggested))
    }

    /** After a pick onboarding continues on Welcome, and the question never comes back. */
    fun answered(state: OnboardingUiState): OnboardingUiState = state.copy(currentStep = OnboardingStep.WELCOME, appLanguageQuestion = null)

    /** The analytics value for a pick: its tag, or "system" for the phone's language. */
    fun analyticsValue(language: AppLanguage): String = language.tag.ifEmpty { "system" }
}

/**
 * Corner radii, in dp, for the tiles of the other languages: two columns joined into one block, with
 * large corners on the block's outside and small ones where tiles meet.
 */
internal data class LanguageTileCorners(val topStart: Int, val topEnd: Int, val bottomEnd: Int, val bottomStart: Int) {
    companion object {
        const val OUTER = 26
        const val INNER = 8
        private const val COLUMNS = 2

        fun of(index: Int, count: Int): LanguageTileCorners {
            require(index in 0 until count)
            val rows = (count + COLUMNS - 1) / COLUMNS
            val row = index / COLUMNS
            val column = index % COLUMNS
            val rowSize = if (row == rows - 1) count - (rows - 1) * COLUMNS else COLUMNS
            val first = column == 0
            val last = column == rowSize - 1
            // A lone last tile spans the whole row, so only the last row is open below.
            val lastRow = row == rows - 1
            fun corner(outside: Boolean) = if (outside) OUTER else INNER
            return LanguageTileCorners(
                topStart = corner(row == 0 && first),
                topEnd = corner(row == 0 && last),
                bottomEnd = corner(lastRow && last),
                bottomStart = corner(lastRow && first),
            )
        }
    }
}

/**
 * The greeting at the top of the language question: one word at a time ("Hola", "Hello", "Olá"...),
 * each rising into a masked line, holding, then lifting away. With animations off it rests on the
 * first word, the suggested language's.
 */
internal object LanguageGreetingMotion {
    const val StartMillis = 220L
    const val EnterMillis = 560L
    const val ExitMillis = 240L
    const val WordMillis = 2_400L

    data class Frame(val index: Int, val alpha: Float, val translationFraction: Float)

    fun frame(elapsedMillis: Long, count: Int, animationsDisabled: Boolean = false): Frame {
        if (count <= 0) return Frame(0, 0f, 0f)
        if (animationsDisabled) return Frame(0, 1f, 0f)
        if (elapsedMillis < StartMillis) return Frame(0, 0f, 0.6f)
        val sinceStart = elapsedMillis - StartMillis
        val index = ((sinceStart / WordMillis) % count).toInt()
        val local = sinceStart % WordMillis
        val exitStart = WordMillis - ExitMillis
        if (local >= exitStart) {
            val exit = smoothstep((local - exitStart).toFloat() / ExitMillis)
            return Frame(index, 1f - exit, -0.35f * exit)
        }
        val enter = easeOut(local.toFloat() / EnterMillis)
        return Frame(index, smoothstep(local.toFloat() / (EnterMillis * 0.6f)), 0.6f * (1f - enter))
    }

    private fun smoothstep(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun easeOut(progress: Float): Float {
        val t = 1f - progress.coerceIn(0f, 1f)
        return 1f - t * t * t
    }
}
