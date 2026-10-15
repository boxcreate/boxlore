package cx.aswin.boxlore.feature.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.WbSunny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoChoiceIconsTest {
    @Test
    fun `tone choices get distinct icons and tone wins over topic words`() {
        val icons = EchoChoiceIcons.forChoices(listOf("Optimistic wonder", "Calm contemplation", "Humorous lightness", "Serious analysis"))
        assertEquals(listOf(Icons.Rounded.WbSunny, Icons.Rounded.Eco, Icons.Rounded.SentimentVerySatisfied, Icons.Rounded.Insights), icons)
        assertEquals(Icons.Rounded.SentimentVerySatisfied, EchoChoiceIcons.match("Lighthearted history"))
    }

    @Test
    fun `whole words only, so space is science and never a spa`() {
        assertEquals(Icons.Rounded.Science, EchoChoiceIcons.match("Space and the cosmos"))
        assertEquals(Icons.Rounded.Fingerprint, EchoChoiceIcons.match("Unsolved cold cases"))
        assertNull(EchoChoiceIcons.match("Spaghetti westerns"))
        assertNull(EchoChoiceIcons.match(""))
    }

    @Test
    fun `every choice gets an icon, unmatched ones take distinct topic-free fallbacks`() {
        val three = EchoChoiceIcons.forChoices(listOf("Cold cases", "Space science", "Ancient empires", "Something odd"))
        assertEquals(EchoChoiceIcons.fallbacks.first(), three[3])
        val none = EchoChoiceIcons.forChoices(listOf("विज्ञान", "इतिहास", "अपराध", "हास्य"))
        assertEquals(EchoChoiceIcons.fallbacks, none)
        assertEquals(4, none.distinct().size)
        val mixed = EchoChoiceIcons.forChoices(listOf("Curious facts", "Blue", "Green", "Red"))
        assertEquals(4, mixed.distinct().size)
        assertTrue(mixed.drop(1).none { it == mixed[0] })
    }

    @Test
    fun `the label before a pipe is what matches`() {
        assertEquals(Icons.Rounded.Fingerprint, EchoChoiceIcons.match("True crime | music and comedy too"))
    }
}
