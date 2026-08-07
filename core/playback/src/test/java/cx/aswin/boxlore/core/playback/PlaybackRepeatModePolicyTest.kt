package cx.aswin.boxlore.core.playback

import androidx.media3.common.Player
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlaybackRepeatModePolicyTest {
    @Test
    fun `repeat mode cycles off all one off`() {
        val all = PlaybackRepeatModePolicy.next(Player.REPEAT_MODE_OFF)
        val one = PlaybackRepeatModePolicy.next(all)
        val off = PlaybackRepeatModePolicy.next(one)

        assertEquals(Player.REPEAT_MODE_ALL, all)
        assertEquals(Player.REPEAT_MODE_ONE, one)
        assertEquals(Player.REPEAT_MODE_OFF, off)
    }
}
