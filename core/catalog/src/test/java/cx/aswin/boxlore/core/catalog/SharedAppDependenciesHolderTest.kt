package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.catalog.sync.UserSyncCoordinator
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SharedAppDependenciesHolderTest {
    @AfterEach
    fun tearDown() {
        SharedAppDependenciesHolder.instance = null
    }

    @Test
    fun `require throws when instance is unset`() {
        SharedAppDependenciesHolder.instance = null
        val error =
            assertThrows(IllegalStateException::class.java) {
                SharedAppDependenciesHolder.require()
            }
        assertTrue(error.message!!.contains("SharedAppDependencies not installed"))
    }

    @Test
    fun `userSyncCoordinator defaults to null on interface`() {
        val dummy = object : SharedAppDependencies {
            override val database get() = error("unused")
            override val podcastRepository get() = error("unused")
            override val subscriptionRepository get() = error("unused")
            override val userPreferencesRepository get() = error("unused")
            override val rssPodcastRepository get() = error("unused")
            override val adaptiveCandidateScorer get() = error("unused")
            override val rankingFeedbackRepository get() = error("unused")
            override val adaptiveRankingRepository get() = error("unused")
            override val rankingRuntimeControls get() = error("unused")
            override val historyRecommendationSource get() = error("unused")
            override val subscriptionForegroundSync get() = error("unused")
            override val folderRepository get() = error("unused")
        }
        assertNull(dummy.userSyncCoordinator)
    }

    @Test
    fun `require returns installed instance with userSyncCoordinator`() {
        val fakeCoordinator = UserSyncCoordinator()
        val dummy = object : SharedAppDependencies {
            override val database get() = error("unused")
            override val podcastRepository get() = error("unused")
            override val subscriptionRepository get() = error("unused")
            override val userPreferencesRepository get() = error("unused")
            override val rssPodcastRepository get() = error("unused")
            override val adaptiveCandidateScorer get() = error("unused")
            override val rankingFeedbackRepository get() = error("unused")
            override val adaptiveRankingRepository get() = error("unused")
            override val rankingRuntimeControls get() = error("unused")
            override val historyRecommendationSource get() = error("unused")
            override val subscriptionForegroundSync get() = error("unused")
            override val folderRepository get() = error("unused")
            override val userSyncCoordinator = fakeCoordinator
        }
        SharedAppDependenciesHolder.instance = dummy
        assertEquals(fakeCoordinator, SharedAppDependenciesHolder.require().userSyncCoordinator)
    }
}
