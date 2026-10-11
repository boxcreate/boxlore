package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import android.graphics.BitmapFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EchoAnimationTest {
    private fun loops() = RuntimeEnvironment.getApplication().assets.open("echo/animation-manifest.json").bufferedReader().use { parseEchoLoops(it.readText()) }

    @Test fun `bundled manifest matches real atlas and actual frame timing`() {
        val assets = RuntimeEnvironment.getApplication().assets
        val atlas = assets.open("echo/echo-atlas.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
        val spin = assets.open("echo/transitions/shared-spin/strip.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
        assertEquals(1536, atlas.width)
        assertEquals(1872, atlas.height)
        assertEquals(208, spin.height)
        val loops = loops()
        assertEquals(9, loops.size)
        assertEquals(57, loops.values.sumOf { it.durations.size })
        loops.values.forEach { loop ->
            var elapsed = 0L
            loop.durations.forEachIndexed { index, duration ->
                assertEquals(index, loop.frameAt(elapsed))
                assertEquals(index, loop.frameAt(elapsed + duration - 1))
                elapsed += duration
            }
            assertEquals(0, loop.frameAt(loop.duration))
            assertEquals(0, loop.frameAt(-1))
        }
        atlas.recycle()
        spin.recycle()
    }

    @Test fun `rapid outfit requests keep one spin and reveal the latest destination`() {
        val playback = EchoPlayback(loops(), EchoLoop(0, listOf(70, 50, 40, 40, 40, 50, 60, 90)), "idle")
        assertEquals("idle", playback.frame(0, "comedy").first.state)
        assertEquals("spin", playback.frame(120, "history").first.state)
        val reveal = playback.frame(520, "syncing")
        assertEquals("syncing", reveal.second?.state)
        assertTrue(reveal.second!!.alpha in 0f..1f)
        assertEquals("syncing", playback.frame(560, "syncing").first.state)
        assertEquals(0, playback.frame(560, "syncing").first.frame)
        assertEquals(1, playback.frame(760, "syncing").first.frame)
    }

    @Test fun `unknown outfit falls back to idle and a reveal interruption starts only the latest spin`() {
        val playback = EchoPlayback(loops(), EchoLoop(0, listOf(70, 50, 40, 40, 40, 50, 60, 90)), "unknown")
        assertEquals("idle", playback.frame(0, "unknown").first.state)
        playback.frame(10, "comedy")
        assertEquals("comedy", playback.frame(530, "comedy").second?.state)
        assertEquals("comedy", playback.frame(570, "history").first.state)
        assertEquals("spin", playback.frame(690, "fiction").first.state)
        assertEquals("fiction", playback.frame(1090, "fiction").second?.state)
    }
}
