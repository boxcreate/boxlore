package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FolderFlowLogicTest {

    private val scope = SaverScope { true }

    private fun <T : Any> roundTrip(saver: Saver<T, Any>, value: T): T {
        val saved = with(saver) { scope.save(value) }
        return saver.restore(requireNotNull(saved))!!
    }

    @Test
    fun `pick shows needs a show, then a name, before the look`() {
        var draft = NewFolderDraft()
        assertEquals(3, draft.stepCount)
        assertFalse(NewFolderFlowLogic.canGoNext(draft))
        assertEquals(draft, NewFolderFlowLogic.next(draft))

        draft = NewFolderFlowLogic.next(NewFolderFlowLogic.togglePick(draft, "pod-a"))
        assertEquals(1, draft.step)
        assertFalse(NewFolderFlowLogic.canGoNext(draft))

        draft = NewFolderFlowLogic.next(NewFolderFlowLogic.withName(draft, "Commute", matchingIconKey = null))
        assertEquals(2, draft.step)
        assertTrue(draft.isLastStep)
        assertEquals(draft, NewFolderFlowLogic.next(draft))
    }

    @Test
    fun `unpicking a show takes it back out`() {
        val picked = NewFolderFlowLogic.togglePick(NewFolderFlowLogic.togglePick(NewFolderDraft(), "pod-a"), "pod-b")
        assertEquals(listOf("pod-b"), NewFolderFlowLogic.togglePick(picked, "pod-a").pickedIds)
    }

    @Test
    fun `one genre names the folder and takes its icon unless the listener chose them`() {
        val fresh = NewFolderFlowLogic.withMode(NewFolderDraft(), NewFolderMode.OneGenre)
        val picked = NewFolderFlowLogic.withGenre(fresh, "News", genreIconKey = "news")
        assertEquals("News", picked.name)
        assertEquals("news", picked.icon)
        assertTrue(NewFolderFlowLogic.canGoNext(picked))

        val named = NewFolderFlowLogic.withIcon(NewFolderFlowLogic.withName(fresh, "Morning", matchingIconKey = null), "coffee")
        val kept = NewFolderFlowLogic.withGenre(named, "News", genreIconKey = "news")
        assertEquals("Morning", kept.name)
        assertEquals("coffee", kept.icon)
    }

    @Test
    fun `changing what goes in starts again from the first step`() {
        val onStepTwo = NewFolderDraft(step = 1, pickedIds = listOf("pod-a"), name = "Commute")
        val switched = NewFolderFlowLogic.withMode(onStepTwo, NewFolderMode.OneGenre)
        assertEquals(0, switched.step)
        assertEquals(2, switched.stepCount)
        assertEquals(onStepTwo, NewFolderFlowLogic.withMode(onStepTwo, NewFolderMode.PickShows))
        assertEquals(1, NewFolderFlowLogic.withMode(onStepTwo, NewFolderMode.AllGenres).stepCount)
    }

    @Test
    fun `the ways to fill a folder are Shows, Genre and Auto`() {
        assertEquals(listOf("Shows", "Genre", "Auto"), NewFolderMode.entries.map(::newFolderModeLabel))
    }

    @Test
    fun `back steps back and is null on the first step`() {
        assertEquals(1, NewFolderFlowLogic.back(NewFolderDraft(step = 2))?.step)
        assertNull(NewFolderFlowLogic.back(NewFolderDraft()))
    }

    @Test
    fun `only a one genre folder fills from its genre`() {
        val genre = NewFolderDraft(mode = NewFolderMode.OneGenre, genre = "News", name = "Headlines", icon = "news", tileShowsCovers = false)
        val genreParams = NewFolderFlowLogic.saveParams(genre)
        assertEquals("News", genreParams.linkedGenre)
        assertFalse(genreParams.showPodcastGrid)

        val picked = NewFolderDraft(genre = "News", name = " Commute ", icon = null, size = FolderDisplaySize.PANEL)
        val pickedParams = NewFolderFlowLogic.saveParams(picked)
        assertNull(pickedParams.linkedGenre)
        assertEquals("Commute", pickedParams.name)
        assertEquals(FolderDisplaySize.PANEL, pickedParams.displaySize)
        // No icon means the tile shows covers.
        assertTrue(pickedParams.showPodcastGrid)
    }

    @Test
    fun `genre names are spotted and Technology is saved as Tech`() {
        assertTrue(NewFolderFlowLogic.nameIsGenre(" news "))
        assertTrue(NewFolderFlowLogic.nameIsGenre("Tech"))
        assertFalse(NewFolderFlowLogic.nameIsGenre("Commute"))
        assertEquals("Saved as Tech", NewFolderFlowLogic.savedNameNote("Technology"))
        assertNull(NewFolderFlowLogic.savedNameNote("Tech"))
    }

    @Test
    fun `a new folder draft survives saved state`() {
        val draft = NewFolderDraft(
            mode = NewFolderMode.OneGenre,
            step = 1,
            pickedIds = listOf("pod-a", "pod-b"),
            search = "rad",
            genre = "News",
            name = "Headlines",
            nameEdited = true,
            icon = "news",
            iconChosen = true,
            size = FolderDisplaySize.SHOWCASE,
            tileShowsCovers = false,
            autoFixedLook = FolderDisplaySize.COMPACT,
        )
        assertEquals(draft, roundTrip(NewFolderDraftSaver, draft))
        assertEquals(NewFolderDraft(), roundTrip(NewFolderDraftSaver, NewFolderDraft()))
    }

    @Test
    fun `edit draft starts from the folder and saves it trimmed`() {
        val folder = SubscriptionFolder(id = "f", name = "News", icon = "news", displaySize = FolderDisplaySize.SHELF, linkedGenre = " News ", showPodcastGrid = false)
        val draft = EditFolderDraft.from(folder)
        assertEquals("News", draft.genre)
        assertFalse(draft.tileShowsCovers)

        val saved = draft.copy(name = "  Headlines ", icon = " ", tileShowsCovers = false).applyTo(folder)
        assertEquals("Headlines", saved.name)
        assertNull(saved.icon)
        // A folder without an icon always shows covers.
        assertTrue(saved.showPodcastGrid)
        assertEquals("News", saved.linkedGenre)
        assertFalse(draft.copy(name = " ").canSave())
    }

    @Test
    fun `an edit draft survives saved state`() {
        val draft = EditFolderDraft(name = "Commute", icon = null, size = FolderDisplaySize.PANEL, tileShowsCovers = true, genre = null)
        assertEquals(draft, roundTrip(EditFolderDraftSaver, draft))
    }

    @Test
    fun `add to folders changes only folders the listener fills`() {
        val folders = listOf(
            SubscriptionFolder(id = "mine", name = "Mine", podcastIds = listOf("pod-a")),
            SubscriptionFolder(id = "later", name = "Later"),
            SubscriptionFolder(id = "news", name = "News", linkedGenre = "News", podcastIds = listOf("pod-a")),
        )
        val change = folderMembershipChange("pod-a", folders, tickedFolderIds = setOf("later"))
        assertEquals(listOf("later"), change.addTo)
        assertEquals(listOf("mine"), change.removeFrom)
        assertEquals(FolderMembershipChange(emptyList(), emptyList()), folderMembershipChange("pod-a", folders, setOf("mine", "news")))
    }

    @Test
    fun `folder pages survive saved state`() {
        val state = FolderPagesState()
        state.openFolder("f1")
        state.openNewFolder(withShowId = "pod-a", movesFrom = "f1")
        state.newFolderDraft = NewFolderFlowLogic.next(state.newFolderDraft)
        state.addToFoldersPodcastId = "pod-b"

        val restored = roundTrip(FolderPagesState.Saver, state)
        assertEquals("f1", restored.openFolderId)
        assertEquals(FolderEditor.NewFolder, restored.editor)
        assertEquals(1, restored.newFolderDraft.step)
        assertEquals(listOf("pod-a"), restored.newFolderDraft.pickedIds)
        assertEquals(FolderShowMove("pod-a", "f1"), restored.newFolderMove)
        assertEquals("pod-b", restored.addToFoldersPodcastId)

        val folder = SubscriptionFolder(id = "f2", name = "Commute")
        state.openEdit(folder)
        state.editDraft = state.editDraft?.copy(name = "Drive")
        val editing = roundTrip(FolderPagesState.Saver, state)
        assertEquals(FolderEditor.Edit("f2"), editing.editor)
        assertEquals("Drive", editing.editDraft?.name)
        assertNull(editing.newFolderMove)
    }

    @Test
    fun `editors encode to saved text and back`() {
        listOf(FolderEditor.NewFolder, FolderEditor.AutoOrganize, FolderEditor.Edit("edit:odd")).forEach { editor ->
            assertEquals(editor, FolderEditor.decode(FolderEditor.encode(editor)))
        }
        assertNull(FolderEditor.decode("something else"))
    }

    @Test
    fun `deleting a folder closes its pages and nothing else`() {
        val state = FolderPagesState()
        state.openFolder("f1")
        state.openEdit(SubscriptionFolder(id = "f1", name = "News"))
        state.onFolderDeleted("f1")
        assertNull(state.openFolderId)
        assertNull(state.editor)
        assertNull(state.editDraft)

        state.openFolder("f2")
        state.openAutoOrganize()
        state.onFolderDeleted("f1")
        assertEquals("f2", state.openFolderId)
        assertEquals(FolderEditor.AutoOrganize, state.editor)
    }

    @Test
    fun `a folder opened from auto-organize replaces it`() {
        val state = FolderPagesState()
        state.openAutoOrganize()
        state.openFolderFromAutoOrganize("news")
        assertNull(state.editor)
        assertEquals("news", state.openFolderId)
    }

    @Test
    fun `a new folder only moves a show when it came from a folder`() {
        val state = FolderPagesState()
        state.openNewFolder(withShowId = "pod-a")
        assertNull(state.newFolderMove)
        assertEquals(listOf("pod-a"), state.newFolderDraft.pickedIds)
        state.closeEditor()
        state.openNewFolder()
        assertEquals(NewFolderDraft(), state.newFolderDraft)
    }

    @Test
    fun `closing everything leaves no page over the Shows tab`() {
        val state = FolderPagesState()
        state.openFolder("f1")
        state.openEdit(SubscriptionFolder(id = "f1", name = "News"))
        state.closeAll()
        assertNull(state.openFolderId)
        assertNull(state.editor)
    }

    @Test
    fun `matching shows for a genre come from the shared genre filter`() {
        val news = TestFixtures.podcast(id = "n", genre = "News")
        val comedy = TestFixtures.podcast(id = "c", genre = "Comedy")
        assertEquals(listOf("n"), matchingShows(listOf(news, comedy), "News").map { it.id })
    }
}
