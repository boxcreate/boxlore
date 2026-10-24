package cx.aswin.boxlore.core.analytics

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Every event that carries `episode_id` declares the optional `recipe_id` beside it, in the CSV
 * and the Markdown glossary, and `home_surface_impression` declares `recipe_ids`. Keeps
 * [AnalyticsGlossary.EPISODE_ID_EVENTS] in step with both files.
 */
class GlossaryRecipeIdTest {
    private val projectRoot: File =
        System
            .getProperty("boxlore.projectRoot")
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: File(".").canonicalFile.let { dir ->
                generateSequence(dir) { it.parentFile }
                    .firstOrNull { File(it, "docs/analytics/event_glossary.csv").isFile }
                    ?: dir
            }

    private val csvProperties: Map<String, List<String>> by lazy {
        File(projectRoot, "docs/analytics/event_glossary.csv")
            .readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .associate { line ->
                val cols = line.split(',')
                cols[0].trim() to cols[PROPERTIES_COLUMN].split(';').map(String::trim)
            }
    }

    private val markdownRows: Map<String, String> by lazy {
        File(projectRoot, "docs/ANALYTICS_EVENT_GLOSSARY.md")
            .readLines()
            .mapNotNull { line ->
                val name = Regex("""^\| `([a-z_]+)` \|""").find(line)?.groupValues?.get(1) ?: return@mapNotNull null
                if (line.split(" | ").size != MARKDOWN_COLUMNS) return@mapNotNull null
                name to line
            }.toMap()
    }

    @Test
    fun `the events that carry episode_id are exactly the recipe id events`() {
        val withEpisodeId = csvProperties.filterValues { props -> props.any { it.startsWith("episode_id:") } }.keys

        assertEquals(AnalyticsGlossary.EPISODE_ID_EVENTS, withEpisodeId)
        assertEquals(25, AnalyticsGlossary.EPISODE_ID_EVENTS.size)
        assertTrue(AnalyticsGlossary.ALL_ALLOWED_EVENTS.containsAll(AnalyticsGlossary.EPISODE_ID_EVENTS))
    }

    @Test
    fun `each episode event declares an optional string recipe_id in the CSV`() {
        val withRecipeId = csvProperties.filterValues { props -> props.any { it.startsWith("recipe_id:") } }.keys
        assertEquals(AnalyticsGlossary.EPISODE_ID_EVENTS, withRecipeId)
        AnalyticsGlossary.EPISODE_ID_EVENTS.forEach { event ->
            assertTrue("recipe_id:string:N" in csvProperties.getValue(event), "$event: recipe_id:string:N")
        }
    }

    @Test
    fun `each episode event lists recipe_id as optional in the Markdown glossary`() {
        AnalyticsGlossary.EPISODE_ID_EVENTS.forEach { event ->
            val row = requireNotNull(markdownRows[event]) { "No glossary row for $event" }
            val optional = row.split(" | ")[OPTIONAL_COLUMN]
            assertTrue("recipe_id:string" in optional, "$event row: $row")
        }
    }

    @Test
    fun `home impressions declare the recipe id list`() {
        assertTrue("recipe_ids:list:N" in csvProperties.getValue("home_surface_impression"))
        assertTrue("recipe_ids:list" in markdownRows.getValue("home_surface_impression"))
        assertEquals("recipe_id", AnalyticsGlossary.RECIPE_ID_PROPERTY)
        assertEquals("recipe_ids", AnalyticsGlossary.RECIPE_IDS_PROPERTY)
    }

    private companion object {
        const val PROPERTIES_COLUMN = 5
        const val MARKDOWN_COLUMNS = 5
        const val OPTIONAL_COLUMN = 3
    }
}
