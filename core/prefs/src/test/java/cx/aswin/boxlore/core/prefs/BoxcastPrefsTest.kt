package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoxcastPrefsTest {
    private lateinit var prefs: BoxcastPrefs

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context
            .getSharedPreferences(BoxcastPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        prefs = BoxcastPrefs(context)
    }

    @Test
    fun onboardingDefaultsToIncomplete() {
        assertFalse(prefs.isOnboardingCompleted())
        prefs.setOnboardingCompleted(true)
        assertTrue(prefs.isOnboardingCompleted())
    }

    @Test
    fun userGenresDefaultEmptyAndRoundTrip() {
        assertTrue(prefs.getUserGenres().isEmpty())
        prefs.setUserGenres(setOf("news", "tech"))
        assertEquals(setOf("news", "tech"), prefs.getUserGenres())
    }

    @Test
    fun recommendationsCacheDefaultsAndSave() {
        assertNull(prefs.getCachedRecommendationsJson())
        assertTrue(prefs.isRecommendationsFallback())
        prefs.saveRecommendationsCache("""[{"id":1}]""", isFallback = false)
        assertEquals("""[{"id":1}]""", prefs.getCachedRecommendationsJson())
        assertFalse(prefs.isRecommendationsFallback())
    }

    @Test
    fun featuredVideoShowcaseDismissalPersistsForever() {
        assertFalse(prefs.isFeaturedVideoShowcaseDismissed())
        prefs.dismissFeaturedVideoShowcaseForever()
        assertTrue(prefs.isFeaturedVideoShowcaseDismissed())
    }

    @Test
    fun bylCacheRoundTrip() {
        assertNull(prefs.getCachedBylPodcastId())
        assertNull(prefs.getCachedBylSlot())
        prefs.saveBylCache(
            episodesJson = "[]",
            podcastsJson = "[]",
            podcastId = "42",
            slotKey = "2026-09-08:MORNING",
        )
        assertEquals("42", prefs.getCachedBylPodcastId())
        assertEquals("2026-09-08:MORNING", prefs.getCachedBylSlot())
        assertEquals("[]", prefs.getCachedBylRecommendationsJson())
        assertEquals("[]", prefs.getCachedBylPodcastsJson())
    }

    @Test
    fun bylCacheClearsOnlyForMatchingPodcastIdentity() {
        prefs.saveBylCache(
            episodesJson = "[1]",
            podcastsJson = "[2]",
            podcastId = "rss:old",
            slotKey = "2026-09-08:NIGHT",
        )
        assertEquals("2026-09-08:NIGHT", prefs.getCachedBylSlot())

        prefs.clearBylCacheIfPodcastId("other")
        assertEquals("rss:old", prefs.getCachedBylPodcastId())
        assertEquals("2026-09-08:NIGHT", prefs.getCachedBylSlot())

        prefs.clearBylCacheIfPodcastId("rss:old")
        assertNull(prefs.getCachedBylPodcastId())
        assertNull(prefs.getCachedBylSlot())
        assertNull(prefs.getCachedBylRecommendationsJson())
        assertNull(prefs.getCachedBylPodcastsJson())
    }

    @Test
    fun learnCuriosityClearRemovesKeys() {
        prefs.setDismissedCuriosityIds(setOf("1", "2"))
        prefs.setLearnCuriosityHistoryJson("""[{"episodeId":"1"}]""")
        prefs.clearLearnCuriosity()
        assertTrue(prefs.getDismissedCuriosityIds().isEmpty())
        assertNull(prefs.getLearnCuriosityHistoryJson())
    }

    @Test
    fun learnerLogEnabledUsesDefaultWhenMissing() {
        assertFalse(prefs.isLearnerLogEnabled(default = false))
        assertTrue(prefs.isLearnerLogEnabled(default = true))
        prefs.setLearnerLogEnabled(true)
        assertTrue(prefs.isLearnerLogEnabled(default = false))
    }

    @Test
    fun resolveLearnerLogEnabled_releaseOffUnlessExplicitOptIn() {
        assertFalse(prefs.resolveLearnerLogEnabled(isDebugBuild = false))
        prefs.setLearnerLogEnabled(true)
        assertTrue(prefs.resolveLearnerLogEnabled(isDebugBuild = false))
        prefs.setLearnerLogEnabled(false)
        assertFalse(prefs.resolveLearnerLogEnabled(isDebugBuild = false))
    }

    @Test
    fun resolveLearnerLogEnabled_debugDefaultsOnWhenUnset() {
        assertTrue(prefs.resolveLearnerLogEnabled(isDebugBuild = true))
        prefs.setLearnerLogEnabled(false)
        assertFalse(prefs.resolveLearnerLogEnabled(isDebugBuild = true))
    }

    @Test
    fun getOrCreateSyncDeviceId_concurrentAccess_returnsSameId() {
        val threadCount = 8
        val executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount)
        val barrier = java.util.concurrent.CyclicBarrier(threadCount)
        val futures = (0 until threadCount).map {
            executor.submit<String> {
                barrier.await()
                prefs.getOrCreateSyncDeviceId()
            }
        }
        val results = futures.map { it.get() }.toSet()
        executor.shutdown()
        executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)

        assertEquals(1, results.size)
        assertTrue(results.first().isNotBlank())
        assertEquals(results.first(), prefs.getOrCreateSyncDeviceId())
    }

    @Test
    fun syncTimestamp_defaultsToZeroAndRoundTrips() {
        assertEquals(0L, prefs.getLastSyncTimestamp())
        prefs.setLastSyncTimestamp(123456789L)
        assertEquals(123456789L, prefs.getLastSyncTimestamp())
    }

    @Test
    fun lastSyncedUserId_defaultsToNullAndRoundTrips() {
        assertNull(prefs.getLastSyncedUserId())
        prefs.setLastSyncedUserId("user_test_abc")
        assertEquals("user_test_abc", prefs.getLastSyncedUserId())
        prefs.setLastSyncedUserId(null)
        assertNull(prefs.getLastSyncedUserId())
    }

    @Test
    fun hasRequestedNotificationPermission_defaultsToFalseAndRoundTrips() {
        assertFalse(prefs.hasRequestedNotificationPermission())
        prefs.setHasRequestedNotificationPermission(true)
        assertTrue(prefs.hasRequestedNotificationPermission())
        prefs.setHasRequestedNotificationPermission(false)
        assertFalse(prefs.hasRequestedNotificationPermission())
    }
}
