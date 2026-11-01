package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers the referrer-parsing branches of [InstallReferrerManager]. `handleReferrer` emits the
 * parsed [ReferralIntent] on [InstallReferrerManager.referralFlow]; needs Robolectric for
 * `android.net.Uri` + SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InstallReferrerManagerTest {
    private fun manager(): InstallReferrerManager = InstallReferrerManager(ApplicationProvider.getApplicationContext<Context>())

    private fun parse(referrer: String): ReferralIntent = runBlocking {
        val mgr = manager()
        withTimeout(2_000) {
            mgr.handleReferrer(referrer)
            mgr.referralFlow.first()
        }
    }

    @Test
    fun parsesQueryStyleReferrerWithTimestamp() {
        val intent = parse("type=episode&id=67890&t=150")
        assertEquals("episode", intent.type)
        assertEquals("67890", intent.id)
        assertEquals(150L, intent.timestamp)
    }

    @Test
    fun parsesQueryStyleReferrerWithPodcastIdAndRange() {
        val intent = parse("type=podcast&podcastId=12345&start=10&end=20")
        assertEquals("podcast", intent.type)
        assertEquals("12345", intent.id)
        assertEquals(10L, intent.start)
        assertEquals(20L, intent.end)
    }

    @Test
    fun parsesUnderscoreStyleReferrer() {
        val intent = parse("type_episode_id_67890_t_150")
        assertEquals("episode", intent.type)
        assertEquals("67890", intent.id)
        assertEquals(150L, intent.timestamp)
    }

    @Test
    fun parsesUnderscorePodcastReferrer() {
        val intent = parse("type_podcast_id_12345")
        assertEquals("podcast", intent.type)
        assertEquals("12345", intent.id)
    }

    @Test
    fun parsesSimpleEpisodeReferrer() {
        val intent = parse("episode_555_t_45")
        assertEquals("episode", intent.type)
        assertEquals("555", intent.id)
        assertEquals(45L, intent.timestamp)
    }

    @Test
    fun parsesSimplePodcastReferrer() {
        val intent = parse("podcast_98765")
        assertEquals("podcast", intent.type)
        assertEquals("98765", intent.id)
    }

    @Test
    fun aShareLinkReferrerKeepsItsShow() {
        val intent = parse("type=episode&id=4503599627370500&show=920666&t=30")
        assertEquals("4503599627370500", intent.id)
        assertEquals("920666", intent.showId)
        assertEquals(30L, intent.timestamp)
        assertEquals("920666", parse("type_episode_id_4503599627370500_show_920666").showId)
    }

    @Test
    fun aReferrerWithoutAValidShowHasNone() {
        assertNull(parse("type=episode&id=67890").showId)
        assertNull(parse("type=episode&id=67890&show=rss:abc").showId)
        assertNull(parse("type=episode&id=67890&show=0920666").showId)
        // Show IDs at or above 2^52 are not Podcast Index shows.
        assertNull(parse("type=episode&id=67890&show=4503599627370496").showId)
    }

    @Test
    fun referralIntentDefaultsAreNull() {
        val intent = ReferralIntent(type = "podcast", id = "1")
        assertNull(intent.timestamp)
        assertNull(intent.start)
        assertNull(intent.end)
        assertNull(intent.showId)
    }

    @Test
    fun deriveInstallChannel_blankIsUnknown() {
        assertEquals("unknown", InstallReferrerManager.deriveInstallChannel(null))
        assertEquals("unknown", InstallReferrerManager.deriveInstallChannel(""))
        assertEquals("unknown", InstallReferrerManager.deriveInstallChannel("   "))
    }

    @Test
    fun deriveInstallChannel_playPlaceholderIsUnknownNotUtm() {
        // Regression: Play's "(not set)" placeholder was counted as a utm campaign.
        assertEquals(
            "unknown",
            InstallReferrerManager.deriveInstallChannel("utm_source=(not%20set)&utm_medium=(not%20set)"),
        )
        assertEquals("utm", InstallReferrerManager.deriveInstallChannel("utm_source=newsletter&utm_medium=email"))
    }

    @Test
    fun deriveInstallChannel_googlePlayIsOrganicBeforeGenericUtm() {
        assertEquals("organic", InstallReferrerManager.deriveInstallChannel("utm_source=google-play"))
        assertEquals(
            "organic",
            InstallReferrerManager.deriveInstallChannel("utm_source=google-play&utm_medium=organic"),
        )
        assertEquals("organic", InstallReferrerManager.deriveInstallChannel("utm_medium=organic"))
    }

    @Test
    fun deriveInstallChannel_sharePatterns() {
        assertEquals("share", InstallReferrerManager.deriveInstallChannel("type=episode&id=1"))
        assertEquals("share", InstallReferrerManager.deriveInstallChannel("type_podcast_id_99"))
        assertEquals("share", InstallReferrerManager.deriveInstallChannel("episode_555_t_45"))
        assertEquals("share", InstallReferrerManager.deriveInstallChannel("podcast_98765"))
    }

    @Test
    fun deriveInstallChannel_genericUtm() {
        assertEquals("utm", InstallReferrerManager.deriveInstallChannel("utm_source=newsletter"))
        assertEquals("utm", InstallReferrerManager.deriveInstallChannel("utm_source=twitter&utm_campaign=launch"))
    }

    @Test
    fun deriveInstallChannel_unrecognizedIsUnknown() {
        assertEquals("unknown", InstallReferrerManager.deriveInstallChannel("some-random-referrer"))
    }

    @Test
    fun emitAttribution_invokesCallbackWithDerivedChannel() {
        val mgr = manager()
        var seenChannel: String? = null
        var seenRaw: String? = "sentinel"
        mgr.onInstallReferrerResolved = { channel, raw ->
            seenChannel = channel
            seenRaw = raw
        }
        mgr.emitAttributionForTest(null)
        assertEquals("unknown", seenChannel)
        assertNull(seenRaw)

        mgr.emitAttributionForTest("utm_source=google-play")
        assertEquals("organic", seenChannel)
        assertEquals("utm_source=google-play", seenRaw)
    }

    @Test
    fun onlyAFreshInstallOpensItsSharedPage() {
        val now = 10L * 24 * 60 * 60 * 1000
        val begunSeconds = now / 1000 - 60
        assertTrue(InstallReferrerManager.isFreshInstall(begunSeconds, now))
        assertTrue("Play didn't say when the install began", InstallReferrerManager.isFreshInstall(0L, now))
        assertFalse(
            "Clear data days later never replays the install's link",
            InstallReferrerManager.isFreshInstall(begunSeconds - 2 * 24 * 60 * 60, now),
        )
        assertFalse(
            "A clock moved back before the install isn't a fresh install",
            InstallReferrerManager.isFreshInstall(now / 1000 + 60, now),
        )
    }

    @Test
    fun aReplayedReferrerIsReportedButNeverOpened() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mgr = InstallReferrerManager(context)
        var seenChannel: String? = null
        mgr.onInstallReferrerResolved = { channel, _ -> seenChannel = channel }
        val now = 10L * 24 * 60 * 60 * 1000

        mgr.processReferrer("type=episode&id=67890", installBeginSeconds = now / 1000 - 3 * 24 * 60 * 60, nowMillis = now)

        assertEquals("share", seenChannel)
        assertTrue(mgr.referralFlow.replayCache.isEmpty())
        assertTrue(referrerProcessed(context))
    }

    @Test
    fun aFreshInstallOpensItsReferrer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mgr = InstallReferrerManager(context)

        mgr.processReferrer("type=episode&id=67890", installBeginSeconds = 0L, nowMillis = 1_000L)

        val intent = withTimeout(2_000) { mgr.referralFlow.first() }
        assertEquals("67890", intent.id)
        assertTrue(referrerProcessed(context))
    }

    private fun referrerProcessed(context: Context): Boolean =
        context
            .getSharedPreferences(PrefsFileMigrator.Files.REFERRER, Context.MODE_PRIVATE)
            .getBoolean("referrer_processed", false)
}
