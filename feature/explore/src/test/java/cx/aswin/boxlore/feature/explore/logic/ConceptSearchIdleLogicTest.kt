package cx.aswin.boxlore.feature.explore.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConceptSearchIdleLogicTest {
    @Test
    fun `examples are unique natural questions`() {
        val examples = ConceptSearchIdleLogic.examples
        assertEquals(4, examples.size)
        assertEquals(examples.size, examples.map { it.query }.toSet().size)
        assertTrue(examples.all { it.query.isNotBlank() && it.label.endsWith("?") })
    }

    @Test
    fun `example queries are the live-checked semantic phrases`() {
        assertEquals(
            listOf(
                "why did the roman empire fall",
                "how do black holes actually work",
                "why people believe conspiracy theories",
                "how money actually works",
            ),
            ConceptSearchIdleLogic.examples.map { it.query },
        )
    }
}
