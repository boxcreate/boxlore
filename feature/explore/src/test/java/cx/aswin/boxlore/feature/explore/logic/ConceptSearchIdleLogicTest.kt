package cx.aswin.boxlore.feature.explore.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConceptSearchIdleLogicTest {
    @Test
    fun `examples are unique natural questions`() {
        val examples = ConceptSearchIdleLogic.examples
        assertEquals(6, examples.size)
        assertEquals(examples.size, examples.map { it.query }.toSet().size)
        assertTrue(examples.all { it.query.isNotBlank() })
        assertEquals(examples.size, examples.map { it.label }.toSet().size)
    }

    @Test
    fun `tall phones and tablets show six examples, shorter windows four`() {
        assertEquals(4, ConceptSearchIdleLogic.visibleExampleCount(799f, usesTabletLayout = false))
        assertEquals(6, ConceptSearchIdleLogic.visibleExampleCount(800f, usesTabletLayout = false))
        assertEquals(6, ConceptSearchIdleLogic.visibleExampleCount(500f, usesTabletLayout = true))
    }

    @Test
    fun `example queries are the live-checked semantic phrases`() {
        assertEquals(
            listOf(
                "why did the roman empire fall",
                "how do black holes actually work",
                "why people believe conspiracy theories",
                "how money actually works",
                "why do we dream",
                "how were the egyptian pyramids built",
            ),
            ConceptSearchIdleLogic.examples.map { it.query },
        )
    }
}
