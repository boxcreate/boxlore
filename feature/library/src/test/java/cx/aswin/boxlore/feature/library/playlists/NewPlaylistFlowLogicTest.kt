package cx.aswin.boxlore.feature.library.playlists

import cx.aswin.boxlore.core.model.PLAYLIST_DEFAULT_PER_SHOW_LIMIT
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NewPlaylistFlowLogicTest {
    private val now = 1_000L * 24 * 60 * 60 * 1000
    private val day = 24L * 60 * 60 * 1000

    private fun show(id: String, genre: String, followedDaysAgo: Long = 400, image: String = "https://img/$id") =
        Podcast(id = id, title = "Show $id", artist = "", imageUrl = image, genre = genre, subscribedAt = now - followedDaysAgo * day)

    private val followed = listOf(
        show("n1", "News"),
        show("n2", "News"),
        show("n3", "News, Politics"),
        show("c1", "Comedy"),
        show("c2", "Comedy", followedDaysAgo = 3),
        show("t1", "True Crime", followedDaysAgo = 10),
        show("h1", "History"),
    )

    private val iconKeys = mapOf("News" to "news", "Comedy" to "comedy", "True Crime" to "crime")

    private fun suggestions(folders: List<SubscriptionFolder> = emptyList(), existing: List<String> = emptyList()) =
        PlaylistSuggestions.suggestions(followed, folders, existing, now, iconKeyFor = { iconKeys[it] }, recentName = "Recently followed")

    @Test
    fun `genres with two or more shows are suggested, biggest first, with their icon and covers`() {
        val genres = suggestions().filter { it.kind == PlaylistSuggestion.Kind.GENRE }
        assertEquals(listOf("News", "Comedy"), genres.map { it.label })
        assertEquals(listOf("n1", "n2", "n3"), genres.first().podcastIds)
        assertEquals("news", genres.first().iconKey)
        assertEquals(listOf("https://img/n1", "https://img/n2", "https://img/n3"), genres.first().covers)
    }

    @Test
    fun `folders the listener made are suggested, genre folders and too-small folders are not`() {
        val folders = listOf(
            SubscriptionFolder("f1", "Mornings", icon = "bulb", podcastIds = listOf("n1", "c1", "gone")),
            SubscriptionFolder("f2", "News", linkedGenre = "News", podcastIds = listOf("n1", "n2")),
            SubscriptionFolder("f3", "Tiny", podcastIds = listOf("h1")),
        )
        val fromFolders = suggestions(folders).filter { it.kind == PlaylistSuggestion.Kind.FOLDER }
        assertEquals(listOf("Mornings"), fromFolders.map { it.label })
        assertEquals(listOf("n1", "c1"), fromFolders.single().podcastIds)
        assertEquals("bulb", fromFolders.single().iconKey)
    }

    @Test
    fun `recent follows make one suggestion, newest first, and a name already used is skipped`() {
        val recent = suggestions().single { it.kind == PlaylistSuggestion.Kind.RECENT }
        assertEquals(listOf("c2", "t1"), recent.podcastIds)
        assertEquals(PlaylistSuggestions.RECENT_ICON_KEY, recent.iconKey)

        val withExisting = suggestions(existing = listOf("  news ", "Recently followed"))
        assertEquals(listOf("Comedy"), withExisting.filter { it.kind == PlaylistSuggestion.Kind.GENRE }.map { it.label })
        assertTrue(withExisting.none { it.kind == PlaylistSuggestion.Kind.RECENT })
    }

    @Test
    fun `nobody followed means no suggestions`() {
        assertTrue(PlaylistSuggestions.suggestions(emptyList(), emptyList(), emptyList(), now, { null }).isEmpty())
    }

    @Test
    fun `a suggestion picks its shows, names the playlist, takes its icon and goes to the last step`() {
        val news = suggestions().first()
        val draft = NewPlaylistFlowLogic.withSuggestion(NewPlaylistFlowLogic.start(), news, "News")
        assertEquals(news.podcastIds, draft.pickedIds)
        assertEquals("News", draft.name)
        assertEquals("news", draft.icon)
        assertEquals(NewPlaylistDraft.FINISH, draft.step)
        assertEquals("genre", draft.origin)
        assertTrue(NewPlaylistFlowLogic.canGoNext(draft))
        // Back from the last step returns to the suggestions.
        assertEquals(NewPlaylistDraft.START, NewPlaylistFlowLogic.back(draft)!!.step)

        // A name and icon the listener set stay.
        val custom = NewPlaylistFlowLogic.start().copy(name = "Mine", nameEdited = true, icon = "star", iconChosen = true)
        val kept = NewPlaylistFlowLogic.withSuggestion(custom, news, "News")
        assertEquals("Mine", kept.name)
        assertEquals("star", kept.icon)
    }

    @Test
    fun `picking yourself needs a show, and one shared genre names the playlist on the way out`() {
        var draft = NewPlaylistFlowLogic.pickOwn(NewPlaylistFlowLogic.start())
        assertEquals(NewPlaylistDraft.PICK, draft.step)
        assertEquals(NewPlaylistDraft.ORIGIN_OWN, draft.origin)
        assertFalse(NewPlaylistFlowLogic.canGoNext(draft))
        draft = NewPlaylistFlowLogic.togglePick(draft, "c1")
        draft = NewPlaylistFlowLogic.togglePick(draft, "c2")
        assertTrue(NewPlaylistFlowLogic.canGoNext(draft))

        val finish = NewPlaylistFlowLogic.next(draft, followed, iconKeyFor = { iconKeys[it] })
        assertEquals(NewPlaylistDraft.FINISH, finish.step)
        assertEquals("Comedy", finish.name)
        assertEquals("comedy", finish.icon)
        assertEquals(NewPlaylistDraft.PICK, NewPlaylistFlowLogic.back(finish)!!.step)

        // Mixed genres leave the name to the listener, and an empty name can't be saved.
        val mixed = NewPlaylistFlowLogic.next(NewPlaylistFlowLogic.togglePick(draft, "h1"), followed, iconKeyFor = { iconKeys[it] })
        assertEquals("", mixed.name)
        assertNull(mixed.icon)
        assertFalse(NewPlaylistFlowLogic.canGoNext(mixed))
        assertTrue(NewPlaylistFlowLogic.canGoNext(NewPlaylistFlowLogic.withName(mixed, "Evenings", null)))
    }

    @Test
    fun `a typed genre name suggests its icon unless one was chosen`() {
        val typed = NewPlaylistFlowLogic.withName(NewPlaylistFlowLogic.start(), "News", "news")
        assertEquals("news", typed.icon)
        assertTrue(typed.nameEdited)
        val chosen = NewPlaylistFlowLogic.withIcon(NewPlaylistFlowLogic.start(), "star")
        assertEquals("star", NewPlaylistFlowLogic.withName(chosen, "News", "news").icon)
    }

    @Test
    fun `genre chips count the pickable shows, and a genre's shows pick and unpick together`() {
        val chips = PlaylistShowGrid.genreChips(followed, inPlaylist = setOf("n1"))
        // Ties sort by name.
        assertEquals(listOf("Comedy" to 2, "News" to 2), chips.take(2))

        val shown = PlaylistShowGrid.visibleShows(followed, setOf("n1"), "News", "")
        assertEquals(listOf("n2", "n3"), shown.map { it.id })
        assertEquals(listOf("c1"), PlaylistShowGrid.visibleShows(followed, emptySet(), null, "show c1").map { it.id })

        val all = NewPlaylistFlowLogic.toggleAll(NewPlaylistFlowLogic.start().copy(pickedIds = listOf("h1")), shown.map { it.id })
        assertEquals(listOf("h1", "n2", "n3"), all.pickedIds)
        assertEquals(listOf("h1"), NewPlaylistFlowLogic.toggleAll(all, shown.map { it.id }).pickedIds)
    }

    @Test
    fun `new playlists take turns with up to three per show until changed`() {
        val start = NewPlaylistFlowLogic.start()
        assertEquals(PlaylistMix.TAKE_TURNS, start.mix)
        assertEquals(PLAYLIST_DEFAULT_PER_SHOW_LIMIT, start.perShowLimit)
        val changed = NewPlaylistFlowLogic.withPlayback(start, PlaylistMix.NEWEST_FIRST, null)
        assertEquals(PlaylistMix.NEWEST_FIRST, changed.mix)
        assertNull(changed.perShowLimit)
    }

    @Test
    fun `add shows is one step that needs a pick and leaves on Back`() {
        val draft = NewPlaylistFlowLogic.addShows("p1")
        assertEquals(1, draft.stepCount)
        assertFalse(NewPlaylistFlowLogic.canGoNext(draft))
        assertTrue(NewPlaylistFlowLogic.canGoNext(NewPlaylistFlowLogic.togglePick(draft, "c1")))
        assertNull(NewPlaylistFlowLogic.back(draft))
    }
}
