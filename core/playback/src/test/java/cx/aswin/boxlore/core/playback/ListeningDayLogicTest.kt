package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.ListeningDayEpisode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Two phones' listening of one day and episode, added up for the history screen. */
class ListeningDayLogicTest {
    @Test
    fun aPartWithoutAKnownLongestThatCouldHideALongerSittingLeavesItUnknown() {
        // An older phone's two hours over two sessions (longest not known), and another phone's 5-minute sitting.
        val older = day(consumedMinutes = 120, sessions = 2, longestMinutes = 0)
        val known = day(consumedMinutes = 5, sessions = 1, longestMinutes = 5)

        val summed = ListeningDayLogic.sum(known, older)

        assertEquals(125 * MINUTE, summed.consumedMs)
        assertEquals(0L, summed.longestSessionMs)
        // A part too short to hold a longer sitting doesn't matter.
        assertEquals(5 * MINUTE, ListeningDayLogic.sum(known, day(consumedMinutes = 4, sessions = 2, longestMinutes = 0)).longestSessionMs)
    }

    private fun day(consumedMinutes: Long, sessions: Int, longestMinutes: Long) = ListeningDayEpisode(
        localDay = 10L,
        episodeId = "ep",
        podcastId = "show",
        consumedMs = consumedMinutes * MINUTE,
        sessionCount = sessions,
        lastListenedAt = 0L,
        longestSessionMs = longestMinutes * MINUTE,
    )

    private companion object {
        const val MINUTE = 60_000L
    }
}
