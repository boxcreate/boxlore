package cx.aswin.boxlore.core.catalog.crosspromo

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Scores the detector against real episodes from the catalog, labelled by hand: cross-promotions of
 * another show (feed drops, introductions, trailers of other shows) and look-alikes that are not (a
 * show's own trailers and new seasons, premium previews, topic "introductions", guest mentions).
 *
 * A detection only counts when its name would also resolve to the labelled show under
 * [CrossPromotionResolver.bestMatch], since the episode page shows nothing otherwise. The detector
 * was tuned on `tune`; `holdout` was held back until late, and `fresh` was sampled and labelled
 * after tuning. Every split must clear the bars. Before this corpus the detector found about one
 * promotion in five.
 */
class CrossPromotionCorpusTest {
    private data class Labeled(
        val id: String,
        val title: String,
        val description: String,
        val podcastTitle: String,
        val duration: Int,
        val otherArt: Boolean,
        val crossPromo: Boolean,
        val promotedShow: String?,
        val split: String,
    )

    private data class Score(val truePositives: Int, val falsePositives: Int, val falseNegatives: Int, val wrongNames: Int) {
        val precision get() = truePositives.toDouble() / (truePositives + falsePositives).coerceAtLeast(1)
        val recall get() = truePositives.toDouble() / (truePositives + falseNegatives + wrongNames).coerceAtLeast(1)
    }

    private val corpus: List<Labeled> by lazy {
        val text = javaClass.classLoader!!.getResource("crosspromo/labeled-episodes.json")!!.readText()
        Gson().fromJson(text, object : TypeToken<List<Labeled>>() {}.type)
    }
    private val detector = CrossPromotionDetector()
    private val resolver = CrossPromotionResolver(search = { emptyList() }, lookup = { null })

    private fun episode(item: Labeled) = Episode(
        id = item.id,
        title = item.title,
        description = item.description,
        audioUrl = "https://example.org/${item.id}.mp3",
        // Only whether the artwork differs matters; the catalog does not keep the episode type.
        imageUrl = if (item.otherArt) "https://art.example/episode/${item.id}.jpg" else "https://art.example/show.jpg",
        podcastImageUrl = "https://art.example/show.jpg",
        duration = item.duration,
    )

    private fun score(items: List<Labeled>, misses: MutableList<String>): Score {
        var tp = 0
        var fp = 0
        var fn = 0
        var wrong = 0
        items.forEach { item ->
            val result = detector.detect(episode(item), item.podcastTitle)
            val name = result.extractedShowName
            when {
                item.crossPromo && result.isCrossPromotion && name != null && resolves(name, item.promotedShow!!) -> tp++
                item.crossPromo && result.isCrossPromotion -> {
                    wrong++
                    misses += "WRONG NAME  [$name] want [${item.promotedShow}]  ${item.title}  (${item.podcastTitle})"
                }
                item.crossPromo -> {
                    fn++
                    misses += "MISSED      [${item.promotedShow}]  ${item.title}  (${item.podcastTitle})"
                }
                result.isCrossPromotion -> {
                    fp++
                    misses += "FALSE ALARM [$name]  ${item.title}  (${item.podcastTitle})"
                }
            }
        }
        return Score(tp, fp, fn, wrong)
    }

    /** Whether the resolver would accept a search result titled [show] for the extracted [name]. */
    private fun resolves(name: String, show: String): Boolean =
        resolver.bestMatch(listOf(Podcast(id = "target", title = show, artist = "", imageUrl = "")), name, hostId = "host") != null

    @Test
    fun `detector finds cross promotions in real episodes without crying wolf`() {
        listOf("tune", "holdout", "fresh").forEach { split ->
            val misses = mutableListOf<String>()
            val items = corpus.filter { it.split == split }
            val score = score(items, misses)
            println("[$split] ${items.size} episodes, ${items.count { it.crossPromo }} promotions: $score precision=${"%.3f".format(score.precision)} recall=${"%.3f".format(score.recall)}")
            misses.sorted().forEach { println("[$split] $it") }
            assertTrue(score.precision >= MinPrecision, "[$split] precision ${score.precision} below $MinPrecision")
            assertTrue(score.recall >= MinRecall, "[$split] recall ${score.recall} below $MinRecall")
        }
    }

    private companion object {
        const val MinPrecision = 0.9
        const val MinRecall = 0.8
    }
}
