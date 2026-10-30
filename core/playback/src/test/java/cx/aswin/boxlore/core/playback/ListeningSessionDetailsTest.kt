package cx.aswin.boxlore.core.playback

import android.media.AudioDeviceInfo
import cx.aswin.boxlore.core.database.ListeningSessionValues
import cx.aswin.boxlore.core.playback.service.ListeningOutputRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

/** What a timed play records besides its time (Room v40): how it started, where it was heard, speed time, release date. */
class ListeningSessionDetailsTest {
    @Test
    fun aPlayStartsFromItsQueueContextElseItsCanonicalEntryPoint() {
        assertEquals(ListeningSessionValues.SOURCE_LORE, ListeningSessionRecordLogic.playSource(QueueMath.CONTEXT_TYPE_LORE, "home_hero_card"))
        assertEquals(ListeningSessionValues.SOURCE_SMART_QUEUE, ListeningSessionRecordLogic.playSource("AUTO_FILL", null))
        assertEquals("home_hero_spotlight", ListeningSessionRecordLogic.playSource("MANUAL", "home_hero_card"))
        assertEquals("mini_player", ListeningSessionRecordLogic.playSource(null, "resume_mini_player"))
        assertNull(ListeningSessionRecordLogic.playSource(null, null))
        assertNull(ListeningSessionRecordLogic.playSource("MANUAL", "something_new"))
    }

    @Test
    fun releaseDatesInSecondsOrMillisecondsAreStoredInMilliseconds() {
        assertEquals(1_700_000_000_000L, ListeningSessionRecordLogic.releaseDateMs(1_700_000_000L))
        assertEquals(1_700_000_000_000L, ListeningSessionRecordLogic.releaseDateMs(1_700_000_000_000L))
        assertNull(ListeningSessionRecordLogic.releaseDateMs(0L))
        assertNull(ListeningSessionRecordLogic.releaseDateMs(null))
    }

    @Test
    fun listeningTimeIsTheAudioPlayedAtItsSpeed() {
        assertEquals(30_000L, ListeningSessionRecordLogic.listenedWallMs(60_000L, 2f))
        assertEquals(60_000L, ListeningSessionRecordLogic.listenedWallMs(60_000L, 1f))
        assertEquals(80_000L, ListeningSessionRecordLogic.listenedWallMs(60_000L, 0.75f))
        // No advance (buffering, paused, a seek back) is no listening time.
        assertEquals(0L, ListeningSessionRecordLogic.listenedWallMs(0L, 1.5f))
        assertEquals(60_000L, ListeningSessionRecordLogic.listenedWallMs(60_000L, 0f))
    }

    @Test
    fun aRecordedPlayCarriesItsDetails() {
        val session =
            ListeningSessionRecordLogic.buildSession(
                ListeningSessionRecordLogic.BuildSessionInput(
                    sessionId = "s",
                    episodeId = "e",
                    podcastId = "p",
                    startedAt = 0L,
                    endedAt = 600_000L,
                    consumedMs = 600_000L,
                    completed = false,
                    zoneId = ZoneOffset.UTC,
                    wallMs = 400_000L,
                    source = "home_mixtape",
                    output = ListeningSessionValues.OUTPUT_HEADPHONES,
                    episodePublishedAt = 9L,
                ),
            )!!

        assertNull(session.deviceId)
        assertEquals(400_000L, session.wallMs)
        assertEquals("home_mixtape", session.source)
        assertEquals(ListeningSessionValues.OUTPUT_HEADPHONES, session.output)
        assertEquals(9L, session.episodePublishedAt)
    }

    @Test
    fun noListeningTimeIsStoredAsUnknown() {
        val session =
            ListeningSessionRecordLogic.buildSession(
                ListeningSessionRecordLogic.BuildSessionInput("s", "e", "p", 0L, 60_000L, 60_000L, completed = false, wallMs = 0L),
            )!!
        assertNull(session.wallMs)
    }

    @Test
    fun theOutputFollowsAndroidsMediaRouteOrder() {
        val speaker = AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        assertEquals(ListeningSessionValues.OUTPUT_BLUETOOTH, ListeningOutputRoute.classify(listOf(speaker, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)))
        assertEquals(ListeningSessionValues.OUTPUT_BLUETOOTH, ListeningOutputRoute.classify(listOf(AudioDeviceInfo.TYPE_BLE_HEADSET)))
        assertEquals(ListeningSessionValues.OUTPUT_HEADPHONES, ListeningOutputRoute.classify(listOf(speaker, AudioDeviceInfo.TYPE_USB_HEADSET)))
        assertEquals(ListeningSessionValues.OUTPUT_SPEAKER, ListeningOutputRoute.classify(listOf(speaker)))
        assertNull(ListeningOutputRoute.classify(listOf(AudioDeviceInfo.TYPE_HDMI)))
        assertNull(ListeningOutputRoute.classify(emptyList()))
    }

    @Test
    fun aProgressSaveFromPlaybackDatesThePlayAndAFinish() {
        val input =
            ListeningHistoryUpsertLogic.ProgressSaveInput(
                podcastId = "p",
                episodeId = "e",
                positionMs = 1_000L,
                durationMs = 60_000L,
                episodeTitle = "Episode",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://cdn.example/e.mp3",
                podcastName = "Show",
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = 5_000L,
            )

        val playing = ListeningHistoryUpsertLogic.buildProgressSaveEntity(input)
        val finished = ListeningHistoryUpsertLogic.buildProgressSaveEntity(input.copy(isCompleted = true))
        val notStarted = ListeningHistoryUpsertLogic.buildProgressSaveEntity(input.copy(positionMs = 0L))

        assertEquals(5_000L to null, playing.firstPlayedAt to playing.finishedAt)
        assertEquals(5_000L to 5_000L, finished.firstPlayedAt to finished.finishedAt)
        assertEquals(null to null, notStarted.firstPlayedAt to notStarted.finishedAt)
    }
}
