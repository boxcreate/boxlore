package cx.aswin.boxlore.feature.info.branded.sherlock

import androidx.compose.runtime.Immutable
import cx.aswin.boxlore.core.model.Episode
import java.util.Locale

/**
 * Sherlock Holmes Short Stories' feed, read by fixed rules that work on any of its episodes. Its
 * titles name the story and the part: "The Sign of Four: Part Three", "The Adventure of the
 * Bruce-Partington Plans: Part Three".
 */
internal object SherlockFeed {
    /** The story: the title before its first ":" or " Part ". */
    fun story(title: String): String = title.split(StoryEnd, limit = 2).first().trim()

    /** The part in Roman numerals when the title ends "Part <word or number>" ("Part Three" is "III"), otherwise null. */
    fun part(title: String): String? {
        val word = PartAtEnd.find(title.trim())?.groupValues?.get(1)?.lowercase(Locale.ROOT) ?: return null
        val number = Numbers.indexOf(word).takeIf { it > 0 } ?: word.toIntOrNull() ?: return null
        return number.takeIf { it in 1..MAX_PART }?.let(::roman)
    }

    /** A story's name as the contents and the card show it: a leading "The Adventure of " dropped, the rest capitalised. */
    fun shortStory(story: String): String = story.replaceFirst(AdventureOf, "").replaceFirstChar { it.titlecase(Locale.ROOT) }

    /** Whether the card sets [title] smaller, on up to two lines: longer than one line of its type holds. */
    fun isLongTitle(title: String): Boolean = title.length > LONG_TITLE

    /**
     * The contents page: the stories the show has read, newest first, each once (a straight and a
     * curly apostrophe count as the same name), [count] at most, numbered as a monthly's pages run
     * (1, 17, 33…). Only full episodes count: trailers and bonus episodes are not stories.
     */
    fun contents(feed: List<Episode>, count: Int = CONTENTS_LINES): List<StrandContentsLine> {
        val seen = HashSet<String>()
        return feed.asSequence()
            .filter { it.episodeType?.equals("full", ignoreCase = true) != false }
            .map { story(it.title) }
            .filter { it.isNotBlank() && seen.add(it.replace('\u2019', '\'').lowercase(Locale.ROOT)) }
            .take(count)
            .mapIndexed { i, name -> StrandContentsLine(shortStory(name), 1 + PAGES_PER_STORY * i) }
            .toList()
    }

    /** The description's first paragraph: the text before its first blank line or paragraph break, without HTML. */
    fun firstParagraph(description: String): String {
        val text = Tag.replace(decodeEntities(decodeEntities(description)), "\n").replace(' ', ' ')
        return text.split(BlankLine).map(String::trim).firstOrNull(String::isNotEmpty)?.replace(Space, " ").orEmpty()
    }

    internal fun roman(number: Int): String {
        var left = number
        return buildString {
            for ((value, letters) in Numerals) {
                while (left >= value) {
                    append(letters)
                    left -= value
                }
            }
        }
    }

    private fun decodeEntities(text: String): String = Entity.replace(text) { match ->
        val name = match.groupValues[1]
        when {
            name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(HEX)?.let(::codePoint)
            name.startsWith("#") -> name.drop(1).toIntOrNull()?.let(::codePoint)
            else -> NamedEntities[name]
        } ?: match.value
    }

    private fun codePoint(code: Int): String? = runCatching { String(Character.toChars(code)) }.getOrNull()

    private val StoryEnd = Regex(":| Part ")
    private val PartAtEnd = Regex("""Part (\w+)$""")
    private val AdventureOf = Regex("""^The Adventure of\s+""")
    private val Tag = Regex("<[^>]+>")
    private val BlankLine = Regex("""\n\s*\n""")
    private val Space = Regex("""\s+""")
    private val Entity = Regex("""&(#?[A-Za-z0-9]+);""")
    private val Numbers = "zero one two three four five six seven eight nine ten eleven twelve".split(' ')
    private val Numerals = listOf(10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I")
    private val NamedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "rsquo" to "’", "lsquo" to "‘", "ldquo" to "“", "rdquo" to "”",
        "mdash" to "—", "ndash" to "–", "hellip" to "…",
    )
    private const val HEX = 16
    private const val MAX_PART = 39
    private const val LONG_TITLE = 17
    private const val CONTENTS_LINES = 9
    private const val PAGES_PER_STORY = 16
}

/** One line of the contents page: a story and the page it "starts" on. */
@Immutable
internal data class StrandContentsLine(val story: String, val page: Int)
