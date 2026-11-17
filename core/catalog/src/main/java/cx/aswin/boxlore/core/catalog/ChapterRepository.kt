package cx.aswin.boxlore.core.catalog

import android.content.Context
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Fetches and parses Podcast 2.0 JSON Chapters from a chaptersUrl.
 * Format: https://github.com/Podcastindex-org/podcast-namespace/blob/main/chapters/jsonChapters.md
 */
object ChapterRepository {

    /** Chapter lists kept in memory; a few Briefs and episodes' worth. */
    private const val CACHE_ENTRIES = 32

    /** How long a kept chapter list is reused before it is read again. */
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

    /**
     * Parsed chapters per address, kept before related-episode IDs are translated, so every return
     * translates them against what this phone holds now.
     */
    private val cache = TimedLruCache<String, List<Chapter>>(maxEntries = CACHE_ENTRIES, ttlMillis = CACHE_TTL_MS)
    private val loads = SharedLoads<String, List<Chapter>>()

    private const val HTTPS_SCHEME = "https://"
    private const val HTTP_SCHEME = "http://"
    private const val HTTPS_PREFIX = "https:"
    private const val HTTP_PREFIX = "http:"

    /** Reads a remote chapters link; tests replace it to serve recorded answers over the https links the app uses. */
    @Volatile
    internal var readRemote: suspend (String) -> String = RemoteText::read

    /** The longest a remote read may take before the chapters count as unavailable. */
    @Volatile
    internal var remoteTimeoutMs: Long = RemoteText.TIMEOUT_MS

    private fun normalizeUrl(url: String): String {
        try {
            var decoded = url
            if (decoded.contains("%3A") || decoded.contains("%2F") || decoded.contains("%3a") || decoded.contains("%2f")) {
                decoded = java.net.URLDecoder.decode(decoded, "UTF-8")
            }
            if (decoded.startsWith(HTTP_PREFIX) && !decoded.startsWith(HTTP_SCHEME)) {
                decoded = decoded.replaceFirst(HTTP_PREFIX, HTTP_SCHEME)
            } else if (decoded.startsWith(HTTPS_PREFIX) && !decoded.startsWith(HTTPS_SCHEME)) {
                decoded = decoded.replaceFirst(HTTPS_PREFIX, HTTPS_SCHEME)
            }
            if (decoded.startsWith(HTTP_SCHEME)) {
                decoded = decoded.replaceFirst(HTTP_SCHEME, HTTPS_SCHEME)
            }
            return decoded
        } catch (e: Exception) {
            return url
        }
    }

    /**
     * The chapters at [chaptersUrl] (a remote link or a saved file). Callers asking for one address at
     * once share one read. A remote read that takes longer than [remoteTimeoutMs] gives no chapters.
     */
    suspend fun getChapters(chaptersUrl: String): List<Chapter> = withContext(Dispatchers.IO) {
        val normalizedUrl = if (chaptersUrl.startsWith("/") || chaptersUrl.startsWith("file:")) {
            chaptersUrl
        } else {
            normalizeUrl(chaptersUrl)
        }
        val chapters = cache[normalizedUrl] ?: loads.load(normalizedUrl) { loadChapters(chaptersUrl, normalizedUrl) }
        toLocalOwners(chapters)
    }

    /** A downloaded episode's saved chapters, with related episodes under the IDs this phone holds. */
    suspend fun getOfflineChapters(context: Context, episodeId: String): List<Chapter> = withContext(Dispatchers.IO) {
        toLocalOwners(ChapterOfflineStorage.getOfflineChapters(context, episodeId))
    }

    // Briefing related episodes come under recipe IDs, including in a downloaded briefing's saved
    // chapters file; show the ones this phone holds under their own IDs.
    private suspend fun toLocalOwners(chapters: List<Chapter>): List<Chapter> =
        DiscoveryRouting.episodeIds.relatedToLocalOwners(chapters)

    /** Reads and parses one address; never throws, so every caller sharing the load gets an answer. */
    private suspend fun loadChapters(chaptersUrl: String, normalizedUrl: String): List<Chapter> {
        cache[normalizedUrl]?.let { return it }
        val chapters = try {
            val json = when {
                chaptersUrl.startsWith("/") -> java.io.File(chaptersUrl).readText()
                chaptersUrl.startsWith("file://") -> java.io.File(chaptersUrl.removePrefix("file://")).readText()
                chaptersUrl.startsWith("file:") -> java.io.File(chaptersUrl.removePrefix("file:")).readText()
                else -> withTimeout(remoteTimeoutMs) { readRemote(normalizedUrl) }
            }
            parseChaptersFromJson(json)
        } catch (timedOut: TimeoutCancellationException) {
            // Only this read timed out; a cancelled caller arrives as a plain CancellationException.
            android.util.Log.w("ChapterRepo", "Timed out fetching chapters: $normalizedUrl", timedOut)
            emptyList()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            android.util.Log.w("ChapterRepo", "Failed to fetch chapters: $normalizedUrl", e)
            emptyList()
        }
        if (chapters.isNotEmpty()) {
            cache[normalizedUrl] = chapters
        }
        return chapters
    }

    fun parseChaptersFromJson(json: String): List<Chapter> {
        return try {
            val root = JSONObject(json)
            val chaptersArray = root.optJSONArray("chapters") ?: return emptyList()

            (0 until chaptersArray.length()).map { i ->
                val obj = chaptersArray.getJSONObject(i)
                val recsArray = obj.optJSONArray("relatedEpisodes")
                val related = recsArray?.let { arr ->
                    (0 until arr.length()).map { j ->
                        val epObj = arr.getJSONObject(j)
                        Episode(
                            id = epObj.optStringOrNull("id") ?: "",
                            title = epObj.optStringOrNull("title") ?: "",
                            description = epObj.optStringOrNull("description") ?: "",
                            audioUrl = epObj.optStringOrNull("audioUrl") ?: "",
                            imageUrl = epObj.optStringOrNull("imageUrl"),
                            podcastImageUrl = epObj.optStringOrNull("podcastImageUrl"),
                            podcastTitle = epObj.optStringOrNull("podcastTitle"),
                            podcastId = epObj.optStringOrNull("podcastId"),
                            podcastGenre = epObj.optStringOrNull("podcastGenre"),
                            podcastArtist = epObj.optStringOrNull("podcastArtist"),
                            duration = epObj.optInt("duration", 0),
                            publishedDate = epObj.optLong("publishedDate", 0L)
                        )
                    }
                }
                Chapter(
                    startTime = obj.optDouble("startTime", 0.0),
                    title = obj.optStringOrNull("title") ?: "Chapter ${i + 1}",
                    img = obj.optStringOrNull("img"),
                    url = obj.optStringOrNull("url"),
                    relatedEpisodes = related
                )
            }.sortedBy { it.startTime }
        } catch (e: Exception) {
            android.util.Log.w("ChapterRepo", "Failed to parse chapters JSON", e)
            emptyList()
        }
    }

    fun chaptersToJson(chapters: List<Chapter>): String {
        val root = JSONObject()
        root.put("version", "1.2.0")
        val array = org.json.JSONArray()
        for (ch in chapters) {
            val obj = JSONObject()
            obj.put("startTime", ch.startTime)
            obj.put("title", ch.title)
            ch.img?.let { obj.put("img", it) }
            ch.url?.let { obj.put("url", it) }
            array.put(obj)
        }
        root.put("chapters", array)
        return root.toString()
    }

    fun setCachedChapters(key: String, chapters: List<Chapter>) {
        cache[key] = chapters
    }

    fun getCachedChapters(key: String): List<Chapter>? = cache[key]

    fun clearCache() {
        cache.clear()
    }

    fun hasChaptersInDescription(htmlDescription: String?): Boolean =
        parseChaptersFromDescription(htmlDescription).isNotEmpty()

    /**
     * Parses chapter timestamps from the episode description.
     * Supports both hh:mm:ss and mm:ss formats, and detects timestamps at either start or end of lines.
     */
    fun parseChaptersFromDescription(htmlDescription: String?): List<Chapter> =
        cx.aswin.boxlore.core.catalog.shownotes.ShowNotesParser.parse(htmlDescription).chapters

    private fun JSONObject.optStringOrNull(name: String, fallback: String? = null): String? {
        if (isNull(name)) return fallback
        val value = optString(name)
        return value.takeIf { it.isNotEmpty() && it != "null" } ?: fallback
    }
}
