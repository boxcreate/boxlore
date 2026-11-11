package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoArtworkSourceStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AutoArtworkSourceStore.clearMemoryForTests()
        context
            .getSharedPreferences(AutoArtworkSourceStore.SOURCE_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun putReturnsTrueWhenCommitSucceeds() {
        assertTrue(AutoArtworkSourceStore.put(context, "ok", "https://cdn.example/ok.jpg"))
        assertEquals("https://cdn.example/ok.jpg", AutoArtworkSourceStore.get(context, "ok"))
    }

    @Test
    fun putIsReadableImmediatelyFromMemoryAndPrefs() {
        assertTrue(AutoArtworkSourceStore.put(context, "abc", "https://cdn.example/cover.jpg"))

        assertEquals(
            "https://cdn.example/cover.jpg",
            AutoArtworkSourceStore.get(context, "abc"),
        )
        // Simulate a fresh reader that only has prefs (e.g. after process restart).
        AutoArtworkSourceStore.flushPersistsForTests()
        AutoArtworkSourceStore.clearMemoryForTests()
        assertEquals(
            "https://cdn.example/cover.jpg",
            AutoArtworkSourceStore.get(context, "abc"),
        )
    }

    @Test
    fun getReturnsNullWhenUnset() {
        assertNull(AutoArtworkSourceStore.get(context, "missing"))
    }

    @Test
    fun remoteUriPersistsMappingBeforeReturning() {
        val uri = AutoArtworkRepository.remoteUri(context, "https://cdn.example/art.png")!!
        val key = uri.pathSegments.last()

        assertEquals(
            "https://cdn.example/art.png",
            AutoArtworkSourceStore.get(context, key),
        )
        AutoArtworkSourceStore.flushPersistsForTests()
        AutoArtworkSourceStore.clearMemoryForTests()
        assertEquals(
            "https://cdn.example/art.png",
            AutoArtworkSourceStore.get(context, key),
        )
    }

    private fun savedOnDisk(): Map<String, *> = context.getSharedPreferences(AutoArtworkSourceStore.SOURCE_PREFS, Context.MODE_PRIVATE).all

    /**
     * A store that refuses every save, once [release] lets the save finish. Like Android's own
     * preferences, it shows a value from the moment its save starts, and goes on showing it after
     * the save has failed.
     */
    private fun contextWhoseSavesFail(started: CountDownLatch, release: CountDownLatch, onDisk: Map<String, String> = emptyMap()): Context {
        val staged = java.util.concurrent.ConcurrentHashMap<String, String>()
        val shown = java.util.concurrent.ConcurrentHashMap(onDisk)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(editor.putString(Mockito.anyString(), Mockito.anyString())).thenAnswer { call ->
            staged[call.getArgument(0)] = call.getArgument(1)
            editor
        }
        `when`(editor.commit()).thenAnswer {
            shown.putAll(staged)
            started.countDown()
            release.await(2, TimeUnit.SECONDS)
            false
        }
        val prefs = mock(SharedPreferences::class.java)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(prefs.getString(Mockito.anyString(), Mockito.isNull())).thenAnswer { call -> shown[call.getArgument<String>(0)] }
        return object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }
    }

    @Test
    fun aSourceStillBeingSavedIsNotVouchedForByASecondCall() {
        // Regression: the second call found the source already showing (in memory, and in the
        // preferences, which show a value before it is on disk) and reported it saved while the first
        // call's save was still pending. When that save failed, the car had been given a link whose
        // source was gone after a restart.
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val failing = contextWhoseSavesFail(started, release)
        var first = true
        var second = true

        val firstCall = thread { first = AutoArtworkSourceStore.put(failing, "racing", "https://cdn.example/a.jpg") }
        assertTrue(started.await(2, TimeUnit.SECONDS))
        val secondCall = thread { second = AutoArtworkSourceStore.put(failing, "racing", "https://cdn.example/a.jpg") }
        // The second call is now waiting on a save of its own, queued behind the first.
        Thread.sleep(100)
        release.countDown()
        firstCall.join(5_000)
        secondCall.join(5_000)

        assertFalse(first)
        assertFalse(second)
        assertNull(AutoArtworkSourceStore.get(failing, "racing"))
        // The preferences still show the value whose save failed; a later call must save it itself.
        assertFalse(AutoArtworkSourceStore.put(failing, "racing", "https://cdn.example/a.jpg"))
        // After a restart nothing is left either: memory is gone and the disk never had it.
        AutoArtworkSourceStore.clearMemoryForTests()
        assertNull(AutoArtworkSourceStore.get(context, "racing"))
    }

    @Test
    fun aSourceSavedByAnEarlierRunNeedsNoSecondSave() {
        // Every save fails here, so a true answer can only come from what the disk already holds.
        val saveStarted = CountDownLatch(1)
        val earlierRun = contextWhoseSavesFail(saveStarted, CountDownLatch(0), onDisk = mapOf("earlier" to "https://cdn.example/earlier.jpg"))

        assertTrue(AutoArtworkSourceStore.put(earlierRun, "earlier", "https://cdn.example/earlier.jpg"))

        assertEquals(1L, saveStarted.count)
    }

    @Test
    fun pruningKeepsWhatThisRunUsedAndForgetsTheRest() {
        context.getSharedPreferences(AutoArtworkSourceStore.SOURCE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("old1", "https://cdn.example/old1.jpg")
            .putString("old2", "https://cdn.example/old2.jpg")
            .putString("old3", "https://cdn.example/old3.jpg")
            .commit()
        assertTrue(AutoArtworkSourceStore.put(context, "used", "https://cdn.example/used.jpg"))
        assertEquals("https://cdn.example/old1.jpg", AutoArtworkSourceStore.get(context, "old1"))
        AutoArtworkSourceStore.flushPersistsForTests()

        AutoArtworkSourceStore.pruneIfOversized(context, maxSaved = 3)

        assertEquals(setOf("used", "old1"), savedOnDisk().keys)
        assertNull(AutoArtworkSourceStore.get(context, "old2"))
    }

    @Test
    fun aSourceDroppedByPruningIsSavedAgainWhenAListNeedsIt() {
        // Regression: a source the prune had dropped could still look saved, so putting it back
        // skipped the save and the link stopped working after a restart.
        context.getSharedPreferences(AutoArtworkSourceStore.SOURCE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("old1", "https://cdn.example/old1.jpg")
            .putString("old2", "https://cdn.example/old2.jpg")
            .commit()
        AutoArtworkSourceStore.pruneIfOversized(context, maxSaved = 1)
        assertTrue(savedOnDisk().isEmpty())

        assertTrue(AutoArtworkSourceStore.put(context, "old1", "https://cdn.example/old1.jpg"))

        AutoArtworkSourceStore.flushPersistsForTests()
        AutoArtworkSourceStore.clearMemoryForTests()
        assertEquals("https://cdn.example/old1.jpg", AutoArtworkSourceStore.get(context, "old1"))
    }

    @Test
    fun aStoreWithinItsLimitIsNotPruned() {
        context.getSharedPreferences(AutoArtworkSourceStore.SOURCE_PREFS, Context.MODE_PRIVATE).edit()
            .putString("old1", "https://cdn.example/old1.jpg")
            .commit()

        AutoArtworkSourceStore.pruneIfOversized(context, maxSaved = 1)

        assertEquals(setOf("old1"), savedOnDisk().keys)
    }
}
