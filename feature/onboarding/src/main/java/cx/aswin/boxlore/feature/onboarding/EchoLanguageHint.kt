package cx.aswin.boxlore.feature.onboarding

import java.util.Locale

/** A coverage hint, not a language classifier or a restriction on what listeners can enter. */
internal object EchoLanguageHint {
    private const val accents = "áéíóúñüçàèùâêîôûëïäößæœ"
    private val words = setOf(
        "hola", "gracias", "musica", "futbol", "ciencia", "tecnologia", "salud", "negocios", "deportes", "historia", "comedia", "noticias",
        "bonjour", "merci", "histoire", "musique", "comedie", "sante", "nouvelles",
        "hallo", "danke", "musik", "geschichte", "wissenschaft", "nachrichten", "obrigado", "ciao",
    )

    fun show(text: String, localeLanguage: String): Boolean {
        if (localeLanguage.isNotBlank() && localeLanguage != "en") return true
        val normalized = text.lowercase(Locale.ROOT)
        if (normalized.any { it in accents }) return true
        if (normalized.codePoints().anyMatch { code ->
            val script = Character.UnicodeScript.of(code)
            script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.COMMON && script != Character.UnicodeScript.INHERITED
        }
        ) {
            return true
        }
        return normalized.split(Regex("[^\\p{L}]+")).any { it in words }
    }

    fun latestAnswer(state: OnboardingUiState): String = state.aiCustomInputText.ifBlank {
        state.aiHistory.lastOrNull { it.role == "user" }?.parts?.joinToString(" ") { it.text }.orEmpty()
    }
}
