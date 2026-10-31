package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PodcastInfoScreenPartsTest {
    private fun episode(index: Int, vararg persons: Person) = TestFixtures.episode(id = "e$index").copy(persons = persons.toList())

    @Test
    fun `hosts, creators and presenters come first, then everyone by name`() {
        val people = sortedShowPersons(
            listOf(
                episode(1, Person("Zed", "guest"), Person("Mia", "Host")),
                episode(2, Person("Abe", "guest"), Person("Kai", "Creator"), Person("Bea", "presenter")),
                episode(3, Person("Cy")),
            ),
        )
        assertEquals(listOf("Bea", "Kai", "Mia", "Abe", "Cy", "Zed"), people.map { it.name })
    }

    @Test
    fun `a person named twice is listed once, whatever their case or spacing`() {
        val people = sortedShowPersons(
            listOf(
                episode(1, Person("Marques Brownlee", "host")),
                episode(2, Person("  marques brownlee ", "host"), Person("David Imel", "host")),
            ),
        )
        assertEquals(listOf("David Imel", "Marques Brownlee"), people.map { it.name })
    }

    @Test
    fun `only the 15 most recent episodes are read`() {
        val episodes = (1..16).map { episode(it, Person("Person $it")) }
        val names = sortedShowPersons(episodes).map { it.name }
        assertEquals(15, names.size)
        assertEquals(false, "Person 16" in names)
    }

    @Test
    fun `episodes without people give an empty list`() {
        assertEquals(emptyList<Person>(), sortedShowPersons(listOf(TestFixtures.episode(id = "e1").copy(persons = null))))
        assertEquals(emptyList<Person>(), sortedShowPersons(emptyList()))
    }
}
