package cx.aswin.boxlore.core.playback.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CastTransferPolicyTest {
    @Test
    fun `filters receiver placeholders and keeps current playable item selected`() {
        val first = playableItem("first")
        val current = playableItem("current")

        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(MediaItem.EMPTY, first, current),
                currentIndex = 2,
            )

        assertEquals(listOf(first, current), result.mediaItems)
        assertEquals(1, result.currentIndex)
        assertFalse(result.resetPosition)
    }

    @Test
    fun `moves to nearest playable item and resets position when current row is invalid`() {
        val next = playableItem("next")

        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(MediaItem.EMPTY, next),
                currentIndex = 0,
            )

        assertEquals(listOf(next), result.mediaItems)
        assertEquals(0, result.currentIndex)
        assertTrue(result.resetPosition)
    }

    @Test
    fun `uses unset index when receiver provides no playable rows`() {
        val result =
            CastTransferPolicy.sanitize(
                mediaItems = listOf(MediaItem.EMPTY, MediaItem.Builder().setUri(Uri.EMPTY).build()),
                currentIndex = 0,
            )

        assertTrue(result.mediaItems.isEmpty())
        assertEquals(C.INDEX_UNSET, result.currentIndex)
        assertTrue(result.resetPosition)
    }

    private fun playableItem(id: String): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(id)
            .setUri("https://cdn.example.com/$id.mp3")
            .build()
}
