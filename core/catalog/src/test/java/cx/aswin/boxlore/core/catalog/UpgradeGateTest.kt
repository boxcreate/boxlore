package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LegacyRows
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RoomSchemaFixture
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import cx.aswin.boxlore.core.rss.MemoryRenumberedShows
import cx.aswin.boxlore.core.rss.RecipeIdHash
import cx.aswin.boxlore.core.rss.RenumberedShows
import cx.aswin.boxlore.core.rss.RssFeedClient
import cx.aswin.boxlore.core.rss.ports.DownloadCacheRelinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The end-to-end upgrade gate (own-episode-IDs P4 PR 21): a database from each shipped schema
 * (v36, v37, v38) with legacy rows, upgraded to v39 by the production migrations, then read by the
 * real catalog with forced recipe-ID collisions. One owner per episode, no duplicate or replaced
 * rows, synthetic keys unresolved until the feed is read, several old IDs per recipe ID, missing,
 * duplicate and changed GUIDs, the recipe trim set, a GUID starting with U+FEFF (which SQLite stores
 * without the mark), and a renumbered show's history.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class UpgradeGateTest(private val version: Int) {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val feeds = mutableMapOf<String, String>()
    private lateinit var database: BoxLoreDatabase

    @Before
    fun setUp() {
        resetDatabaseInstance()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized) database.close()
        resetDatabaseInstance()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `the upgrade keys every row it can and leaves the rest unresolved`() = runBlocking(Dispatchers.IO) {
        upgrade()
        val aliases = aliases()
        assertEquals(EpisodeAliasState.UNRESOLVED to "upgrade_synthetic_key", aliases.getValue(SYNTHETIC).let { it.state to it.provenance })
        assertEquals(EpisodeAliasState.UNRESOLVED to "upgrade_audio_url_key", aliases.getValue("-20").let { it.state to it.provenance })
        for (id in listOf("-99", "-98", "7002")) assertEquals(id, "upgrade_listener_row", aliases.getValue(id).provenance)
        // Unicode trim: NBSP, U+3000 and U+180E go; U+200B stays.
        assertEquals("g-nl", aliases.getValue("-11").key)
        assertEquals("g-nbsp", aliases.getValue("-13").key)
        assertEquals("g-mvs", aliases.getValue("-14").key)
        assertEquals("g-zw\u200B", aliases.getValue("-16").key)
        // Two old IDs of one episode: one recipe ID, the one with the latest listening owns.
        assertEquals(aliases.getValue("-17").recipeId, aliases.getValue("-18").recipeId)
        assertTrue(aliases.getValue("-18").owner)
        assertFalse(aliases.getValue("-17").owner)
        assertOneOwnerPerEpisode(aliases)
    }

    @Test
    fun `the first reads settle what they can, collide safely and duplicate nothing`() = runBlocking(Dispatchers.IO) {
        upgrade()
        val catalog = readAll()
        val aliases = aliases()
        val rows = rows()

        assertEquals(EpisodeAliasState.TRUSTED to "g-synth", aliases.getValue(SYNTHETIC).let { it.state to it.key })
        assertEquals(EpisodeAliasState.TRUSTED, aliases.getValue("-20").state)
        assertEquals("g-hist" to "feed_listener_match", aliases.getValue("-99").let { it.key to it.provenance })
        assertEquals("never played or stored here: still unresolved", EpisodeAliasState.UNRESOLVED, aliases.getValue("-98").state)

        // A changed GUID is a new episode; the old row keeps its ID, key and history.
        assertEquals("g-old", rows.getValue("-19").guid)
        val renamed = rows.values.single { it.guid == "g-renamed" }
        assertTrue(renamed.id != "-19")
        assertEquals(1, history("-19"))

        // A duplicate GUID: the first item in the document wins.
        assertEquals(audio("dup-1"), rows.values.single { it.guid == "g-dup" }.audio)

        // Forced collisions never replace a row: another show's holder keeps the ID, the clashing
        // items get distinct reserved IDs, and the served ID stays with its own episode.
        assertEquals(FORCED.toString(), rows.values.single { it.show == OTHER && it.guid == "x-clash" }.id)
        val clashes = listOf("g-clash-a", "g-clash-b", "g-clash-served").map { key -> rows.values.single { it.show == SHOW && it.guid == key }.id }
        assertEquals(3, clashes.toSet().size)
        assertTrue(clashes.all { it.toLong() < 0 })
        assertEquals(SERVED, rows.values.single { it.guid == "g-served" }.id)

        // A key starting with U+FEFF keeps its stored episode (SQLite stored it without the mark).
        assertEquals(audio("bom-kept"), rows.getValue("-15").audio)

        assertOneOwnerPerEpisode(aliases)
        assertNotNull(catalog)
    }

    @Test
    fun `a second read changes no episode and no alias`() = runBlocking(Dispatchers.IO) {
        upgrade()
        val catalog = readAll()
        val sql = database.openHelper.writableDatabase
        val before = RoomSchemaFixture.snapshot(sql)
        catalog.refresh(request(SHOW))
        val after = RoomSchemaFixture.snapshot(sql)
        for (table in listOf("local_episodes", "episode_aliases", "listening_history", "queue_items", "downloaded_episodes")) {
            assertEquals(table, before[table], after[table])
        }
    }

    @Test
    fun `a GUID starting with U+FEFF stays one episode over many reads`() = runBlocking(Dispatchers.IO) {
        upgrade()
        val catalog = readAll()
        feeds[feedUrl(THIRD)] = rss(item("\uFEFFbom-only"))
        database.podcastDao().upsert(PodcastEntity(podcastId = THIRD, title = "t", author = "", imageUrl = "", description = null, isSubscribed = true))
        repeat(3) { catalog.refresh(request(THIRD)) }
        assertEquals("one row, not one per read", 1, rows().values.count { it.show == THIRD })
        assertEquals(1, aliases().values.count { it.show == THIRD })
    }

    @Test
    fun `a renumbered show's history joins the new show's episode`() = runBlocking(Dispatchers.IO) {
        upgrade()
        val shows = MemoryRenumberedShows().apply { record(OLD, NEW) }
        database.podcastDao().upsert(PodcastEntity(podcastId = NEW, title = "n", author = "", imageUrl = "", description = null, isSubscribed = true))
        feeds[feedUrl(NEW)] = rss(item("r-1", audio = RENUMBERED_AUDIO_1), item("r-2", audio = RENUMBERED_AUDIO_2))
        catalog(CLASH_HASH, shows).refresh(request(NEW))
        val joined = aliases().getValue("7002")
        assertEquals(NEW to "renumbered_show_match", joined.show to joined.provenance)
        assertEquals("r-2", joined.key)
        assertEquals("the old show's row and history keep their IDs", OLD, rows().getValue("7001").show)
        assertEquals(1, history("7002"))
    }

    private suspend fun upgrade() {
        RoomSchemaFixture.createFromSchema(context, DATABASE_NAME, version) { fill(it) }
        database = BoxLoreDatabase.getDatabase(context)
        assertEquals(40, database.openHelper.writableDatabase.version)
    }

    private suspend fun readAll(): LocalEpisodeCatalogRepository {
        feeds[feedUrl(SHOW)] = showFeed()
        feeds[feedUrl(OTHER)] = rss(item("x-pi"), item("x-clash"))
        val catalog = catalog(CLASH_HASH, MemoryRenumberedShows())
        assertTrue(catalog.refresh(request(OTHER)) is LocalEpisodeCatalogPort.RefreshOutcome.Success)
        assertTrue(catalog.refresh(request(SHOW)) is LocalEpisodeCatalogPort.RefreshOutcome.Success)
        return catalog
    }

    private data class Alias(val show: String, val key: String?, val recipeId: Long?, val state: String, val owner: Boolean, val provenance: String)

    private data class Row(val id: String, val show: String, val guid: String, val audio: String)

    private fun aliases(): Map<String, Alias> =
        RoomSchemaFixture.rows(database.openHelper.writableDatabase, "episode_aliases", listOf("aliasId", "podcastId", "catalogKey", "recipeId", "state", "owner", "provenance"))
            .rows.associate { r -> r[0].toString() to Alias(r[1].toString(), r[2] as String?, (r[3] as Number?)?.toLong(), r[4].toString(), (r[5] as Number).toInt() == 1, r[6].toString()) }

    private fun rows(): Map<String, Row> =
        RoomSchemaFixture.rows(database.openHelper.writableDatabase, "local_episodes", listOf("episodeId", "podcastId", "guid", "audioUrl"))
            .rows.associate { r -> r[0].toString() to Row(r[0].toString(), r[1].toString(), r[2].toString(), r[3].toString()) }

    private fun history(id: String): Int =
        RoomSchemaFixture.rows(database.openHelper.writableDatabase, "listening_history", listOf("episodeId")).rows.count { it[0].toString() == id }

    /** Every trusted episode (show, key) has exactly one owner. */
    private fun assertOneOwnerPerEpisode(aliases: Map<String, Alias>) {
        aliases.values.filter { it.state == EpisodeAliasState.TRUSTED && it.key != null }
            .groupBy { it.show to it.key }
            .forEach { (episode, group) -> assertEquals("owners of $episode", 1, group.count { it.owner }) }
    }

    private fun fill(db: SupportSQLiteDatabase) {
        val insert = { table: String, row: Map<String, Any?> -> RoomSchemaFixture.insert(db, table, row) }
        for (show in listOf(SHOW, OTHER, OLD)) {
            insert("podcasts", LegacyRows.podcast(show, subscribed = true))
            insert("local_episode_feeds", LegacyRows.feed(show))
        }
        val episode = { id: String, key: String, audio: String -> insert("local_episodes", LegacyRows.localEpisode(id, SHOW, key, audio)) }
        episode("5001", "g-pi", audio("g-pi"))
        episode("-11", "g-nl\n", audio("g-nl"))
        episode(SYNTHETIC, "id:$SYNTHETIC", audio("synthetic"))
        episode("-13", "\u00A0g-nbsp\u3000", audio("g-nbsp"))
        episode("-14", "g-mvs\u180E", audio("g-mvs"))
        episode("-15", "\uFEFFg-bom", audio("bom-kept"))
        episode("-16", "g-zw\u200B", audio("zw-kept"))
        episode("-17", "g-two", audio("g-two"))
        episode("-18", "g-two\t", audio("g-two"))
        episode("-19", "g-old", audio("renamed"))
        episode("-20", audio("noguid"), audio("noguid"))
        episode(SERVED, "g-served", audio("g-served"))
        insert("local_episodes", LegacyRows.localEpisode("6001", OTHER, "x-pi", audio("x-pi")))
        insert("local_episodes", LegacyRows.localEpisode("7001", OLD, "r-1", RENUMBERED_AUDIO_1))
        insert("listening_history", LegacyRows.history("5001", SHOW, lastPlayedAt = 500L))
        insert("listening_history", LegacyRows.history(SYNTHETIC, SHOW, lastPlayedAt = 510L) + ("episodeAudioUrl" to audio("synthetic")))
        insert("listening_history", LegacyRows.history("-18", SHOW, lastPlayedAt = 900L))
        insert("listening_history", LegacyRows.history("-19", SHOW, lastPlayedAt = 700L, likedAt = 750L) + ("isCompleted" to 1))
        insert("listening_history", LegacyRows.history("-99", SHOW, lastPlayedAt = 800L) + ("episodeAudioUrl" to audio("g-hist")))
        insert("listening_history", LegacyRows.history("7001", OLD, lastPlayedAt = 600L) + ("isCompleted" to 1))
        insert("listening_history", LegacyRows.history("7002", OLD, lastPlayedAt = 610L) + mapOf("isCompleted" to 1, "episodeAudioUrl" to RENUMBERED_AUDIO_2))
        insert("queue_items", LegacyRows.queueItem("5001", SHOW, position = 0))
        insert("queue_items", LegacyRows.queueItem("-98", SHOW, position = 1))
        insert("downloaded_episodes", LegacyRows.download("5001", SHOW))
        insert("downloaded_episodes", LegacyRows.download("-17", SHOW))
    }

    private fun showFeed() = rss(
        item("g-pi"),
        item("g-nl"),
        item("g-nbsp\u2003", audio = audio("g-nbsp")),
        item("\u3000g-mvs", audio = audio("g-mvs")),
        item("\uFEFFg-bom", audio = audio("bom-kept")),
        item("g-bom"),
        item("g-zw\u200B", audio = audio("zw-kept")),
        item("g-zw"),
        item("g-two"),
        item("g-renamed", audio = audio("renamed"), title = "Episode -19"),
        item(null, audio = audio("noguid")),
        item("g-served"),
        item("g-dup", audio = audio("dup-1")),
        item("g-dup", audio = audio("dup-2")),
        item("g-hist"),
        item("g-synth", audio = audio("synthetic"), title = "Episode -12"),
        item("g-clash-a"),
        item("g-clash-b"),
        item("g-clash-served"),
    )

    private fun request(show: String) = LocalEpisodeCatalogPort.RefreshRequest(
        podcastIndexId = show,
        feedUrl = feedUrl(show),
        reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
    )

    private fun feedClient() = RssFeedClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            val body = feeds[url]
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (body == null) 404 else 200).message("test")
                .body(body.orEmpty().toResponseBody("application/rss+xml".toMediaType())).build()
        }.build(),
    )

    /** The catalog with a test-only recipe hash: its internal constructor (the collision seam of P4 PR 9). */
    private fun catalog(hash: RecipeIdHash, shows: RenumberedShows): LocalEpisodeCatalogRepository {
        val constructor = LocalEpisodeCatalogRepository::class.java.declaredConstructors.single { candidate ->
            candidate.parameterTypes.toList() == listOf(
                BoxLoreDatabase::class.java,
                RssFeedClient::class.java,
                DownloadCacheRelinker::class.java,
                Function2::class.java,
                RecipeIdHash::class.java,
                RenumberedShows::class.java,
            )
        }
        val persisted: suspend (String) -> Unit = {}
        return constructor.newInstance(database, feedClient(), DownloadCacheRelinker { _, _ -> false }, persisted, hash, shows) as LocalEpisodeCatalogRepository
    }

    private fun resetDatabaseInstance() {
        BoxLoreDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }.set(null, null)
    }

    private data class FeedItem(val guid: String?, val audio: String, val title: String)

    private fun item(guid: String?, audio: String = audio(guid.orEmpty()), title: String = "Episode ${guid.orEmpty().trim()}") = FeedItem(guid, audio, title)

    private fun rss(vararg items: FeedItem): String {
        val body = items.withIndex().joinToString("") { (index, item) ->
            val guid = item.guid?.let { "<guid isPermaLink=\"false\">${xml(it)}</guid>" }.orEmpty()
            "<item><title>${xml(item.title)}</title>$guid<pubDate>${pubDate(index)}</pubDate>" +
                "<enclosure url=\"${item.audio}\" type=\"audio/mpeg\" length=\"1\"/></item>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><rss version=\"2.0\"><channel><title>Show</title>$body</channel></rss>"
    }

    private fun xml(value: String) = value.map { c -> if (c.code > 126 || c.code < 32) "&#x%X;".format(c.code) else c.toString() }.joinToString("")

    private fun pubDate(index: Int): String =
        java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.Instant.ofEpochSecond(1_790_000_000L - index * 86_400L).atZone(java.time.ZoneOffset.UTC))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "from v{0}")
        fun versions(): List<Array<Any>> = listOf(arrayOf(36), arrayOf(37), arrayOf(38))

        const val DATABASE_NAME = "boxlore_database"
        const val SHOW = "920666"
        const val OTHER = "920667"
        const val OLD = "1258562"
        const val NEW = "7976624"
        const val THIRD = "920668"
        val SYNTHETIC = cx.aswin.boxlore.core.rss.RssIdGenerator.episodeId(feedUrl(SHOW), "g-synth", audio("synthetic"), 0L, "")
        const val FORCED = 4_600_000_000_000_000L
        val SERVED = EpisodeRecipeId.forNamespace(SHOW.toLong(), "g-served").toString()
        const val RENUMBERED_AUDIO_1 = "https://sphinx.example/r-1.mp3"
        const val RENUMBERED_AUDIO_2 = "https://sphinx.example/r-2.mp3"
        val CLASH_HASH = RecipeIdHash { epNs, key ->
            when (key) {
                "g-clash-a", "g-clash-b", "x-clash" -> FORCED
                "g-clash-served" -> SERVED.toLong()
                else -> EpisodeRecipeId.forNamespace(epNs, key)
            }
        }

        fun feedUrl(show: String) = "https://feeds.example/$show.xml"

        fun audio(name: String) = "https://cdn.example/$name.mp3"
    }
}
