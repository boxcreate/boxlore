package cx.aswin.boxlore.core.designsystem.list

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LazyListKeyPolicyTest {

    private data class TestItem(val id: String?, val title: String)

    @Test
    fun deduplicateByIdPreservesOrderAndRemovesDuplicates() {
        val items = listOf(
            TestItem("id1", "First"),
            TestItem("id2", "Second"),
            TestItem("id1", "First Duplicate"),
            TestItem("id3", "Third"),
            TestItem("id2", "Second Duplicate"),
        )
        val result = LazyListKeyPolicy.deduplicateById(items) { it.id }

        assertEquals(3, result.size)
        assertEquals("First", result[0].title)
        assertEquals("Second", result[1].title)
        assertEquals("Third", result[2].title)
    }

    @Test
    fun deduplicateByIdDropsNullBlankAndWhitespaceIds() {
        val items = listOf(
            TestItem("id1", "Valid"),
            TestItem(null, "Null ID"),
            TestItem("", "Empty ID"),
            TestItem("   ", "Blank ID"),
            TestItem("id2", "Valid Two"),
        )
        val result = LazyListKeyPolicy.deduplicateById(items) { it.id }

        assertEquals(2, result.size)
        assertEquals("id1", result[0].id)
        assertEquals("id2", result[1].id)
    }

    @Test
    fun deduplicateByIdReturnsEmptyListForEmptyInput() {
        val result = LazyListKeyPolicy.deduplicateById<TestItem>(emptyList()) { it.id }
        assertTrue(result.isEmpty())
    }

    @Test
    fun deduplicateByDeduplicatesByCustomKey() {
        val pairs = listOf(
            "news" to "News",
            "tech" to "Technology",
            "news" to "News Duplicate",
            "comedy" to "Comedy",
        )
        val result = LazyListKeyPolicy.deduplicateBy(pairs) { it.first }

        assertEquals(3, result.size)
        assertEquals(listOf("news", "tech", "comedy"), result.map { it.first })
    }

    @Test
    fun safeKeyFormatsPrefixWithoutDoubleUnderscore() {
        assertEquals("grid_123", LazyListKeyPolicy.safeKey("123", prefix = "grid"))
        assertEquals("grid_123", LazyListKeyPolicy.safeKey("123", prefix = "grid_"))
        assertEquals("123", LazyListKeyPolicy.safeKey("123", prefix = ""))
    }

    @Test
    fun safeKeyFallsBackToIndexWhenIdIsBlankOrNull() {
        assertEquals("item_3", LazyListKeyPolicy.safeKey(null, index = 3))
        assertEquals("item_0", LazyListKeyPolicy.safeKey("", index = 0))
        assertEquals("item_unknown", LazyListKeyPolicy.safeKey(null, index = -1))
        assertEquals("rail_item_2", LazyListKeyPolicy.safeKey("   ", index = 2, prefix = "rail"))
    }

    @Test
    fun disambiguateKeysHandlesUniqueList() {
        val items = listOf(
            TestItem("a", "Alpha"),
            TestItem("b", "Beta"),
            TestItem("c", "Gamma"),
        )
        val keys = LazyListKeyPolicy.disambiguateKeys(items, prefix = "test") { it.id }

        assertEquals(listOf("test_a", "test_b", "test_c"), keys)
    }

    @Test
    fun disambiguateKeysResolvesCollisionsWithOccurrenceSuffix() {
        val items = listOf(
            TestItem("show_1", "First"),
            TestItem("show_2", "Second"),
            TestItem("show_1", "Duplicate"),
            TestItem("show_1", "Triplicate"),
        )
        val keys = LazyListKeyPolicy.disambiguateKeys(items, prefix = "rail") { it.id }

        assertEquals(listOf("rail_show_1", "rail_show_2", "rail_show_1_dup_1", "rail_show_1_dup_2"), keys)
        assertEquals(keys.size, keys.distinct().size)
    }

    @Test
    fun disambiguateKeysHandlesNullAndBlankIdsWithoutCollision() {
        val items = listOf(
            TestItem(null, "No ID 0"),
            TestItem("", "Empty ID 1"),
            TestItem("valid", "Valid"),
            TestItem("valid", "Valid Dup"),
            TestItem(null, "No ID 4"),
        )
        val keys = LazyListKeyPolicy.disambiguateKeys(items, prefix = "sec") { it.id }

        assertEquals(5, keys.size)
        assertEquals(keys.size, keys.distinct().size)
        assertEquals("sec_item_0", keys[0])
        assertEquals("sec_item_1", keys[1])
        assertEquals("sec_valid", keys[2])
        assertEquals("sec_valid_dup_1", keys[3])
        assertEquals("sec_item_4", keys[4])
    }

    @Test
    fun buildKeySelectorProvidesDirectAccessByIndex() {
        val items = listOf(
            TestItem("id1", "One"),
            TestItem("id1", "One Dup"),
        )
        val keySelector = LazyListKeyPolicy.buildKeySelector(items, prefix = "p") { it.id }

        assertEquals("p_id1", keySelector(0))
        assertEquals("p_id1_dup_1", keySelector(1))
    }

    @Test
    fun disambiguateKeysPreventsCollisionsWhenInputContainsDuplicatePattern() {
        val items = listOf(
            TestItem("item", "First"),
            TestItem("item", "Second (dup)"),
            TestItem("item_dup_1", "Third with matching suffix name"),
        )
        val keys = LazyListKeyPolicy.disambiguateKeys(items) { it.id }

        assertEquals(3, keys.size)
        assertEquals(3, keys.distinct().size)
        assertEquals(listOf("item", "item_dup_1", "item_dup_1_dup_1"), keys)
    }

    @Test
    fun disambiguateKeysHandlesChainedCollisions() {
        val items = listOf(
            TestItem("k", "0"),
            TestItem("k", "1"),
            TestItem("k_dup_1", "2"),
            TestItem("k_dup_2", "3"),
            TestItem("k", "4"),
        )
        val keys = LazyListKeyPolicy.disambiguateKeys(items, prefix = "rail") { it.id }

        assertEquals(5, keys.size)
        assertEquals(5, keys.distinct().size)
        assertEquals("rail_k", keys[0])
        assertEquals("rail_k_dup_1", keys[1])
        assertEquals("rail_k_dup_1_dup_1", keys[2])
        assertEquals("rail_k_dup_2", keys[3])
        assertEquals("rail_k_dup_3", keys[4])
    }
}
