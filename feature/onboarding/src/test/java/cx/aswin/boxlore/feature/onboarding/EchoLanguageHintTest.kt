package cx.aswin.boxlore.feature.onboarding

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoLanguageHintTest {
    @Test fun `scripts accents and known vocabulary show the coverage hint`() {
        for (answer in listOf("मुझे विज्ञान पसंद है", "日本語", "العربية", "ciencia", "musique", "café")) {
            assertTrue(EchoLanguageHint.show(answer, "en"), answer)
        }
        assertTrue(EchoLanguageHint.show("", "hi"))
    }

    @Test fun `English names punctuation and emoji are not treated as another language`() {
        for (answer in listOf("", "Science stories", "The daily news", "Die Hard movies", "😀 20 minutes", "A discussion of melodrama")) {
            assertFalse(EchoLanguageHint.show(answer, "en"), answer)
        }
    }
}
