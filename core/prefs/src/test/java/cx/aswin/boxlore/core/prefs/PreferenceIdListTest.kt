package cx.aswin.boxlore.core.prefs

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PreferenceIdListTest {
    @Test
    fun `round trips ids including rss urls`() {
        val ids = listOf("123", "rss:https://feeds.example/a.xml")
        assertEquals(ids, PreferenceIdList.decode(PreferenceIdList.encode(ids)))
    }

    @Test
    fun `empty and blank decode to empty list`() {
        assertEquals(emptyList<String>(), PreferenceIdList.decode(null))
        assertEquals(emptyList<String>(), PreferenceIdList.decode(""))
        assertEquals(emptyList<String>(), PreferenceIdList.decode("   "))
    }

    @Test
    fun `encode drops blank ids`() {
        assertEquals("a\u001fb", PreferenceIdList.encode(listOf(" a ", "", "b")))
    }
}
