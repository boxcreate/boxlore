package cx.aswin.boxlore.core.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoverySectionPreferencesTest {
    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
        repository = UserPreferencesRepository(context)
    }

    @After
    fun tearDown() {
        runBlocking { context.userPreferencesDataStore.edit { it.clear() } }
    }

    @Test
    fun everySectionStartsOn() = runTest {
        assertEquals(DiscoverySections(), repository.discoverySectionsStream.first())
        DiscoverySection.entries.forEach { section ->
            assertTrue(section.name, repository.discoverySectionsStream.first().isOn(section))
        }
    }

    @Test
    fun eachSwitchPersistsOnItsOwnAndSurvivesRecreation() = runTest {
        DiscoverySection.entries.forEach { section ->
            repository.setDiscoverySection(section, false)
            val reopened = UserPreferencesRepository(context).discoverySectionsStream.first()
            assertEquals(DiscoverySections().with(section, false), reopened)
            repository.setDiscoverySection(section, true)
            assertEquals(DiscoverySections(), UserPreferencesRepository(context).discoverySectionsStream.first())
        }
    }

    @Test
    fun briefSwitchIsTheCardsHideForeverChoice() = runTest {
        repository.dismissBriefingForever()
        assertFalse(repository.discoverySectionsStream.first().dailyBrief)

        repository.setDiscoverySection(DiscoverySection.DAILY_BRIEF, true)
        assertFalse(repository.briefingDismissedForever.first())

        repository.setDiscoverySection(DiscoverySection.DAILY_BRIEF, false)
        assertTrue(repository.briefingDismissedForever.first())
    }

    @Test
    fun turningTheBriefBackOnUndoesTodaysDismissal() = runTest {
        repository.dismissBriefing("2026-10-15")
        repository.setDiscoverySection(DiscoverySection.DAILY_BRIEF, false)

        repository.setDiscoverySection(DiscoverySection.DAILY_BRIEF, true)

        assertEquals("", repository.briefingDismissedDate.first())
        assertTrue(repository.discoverySectionsStream.first().dailyBrief)
    }

    @Test
    fun turningTheBriefOffKeepsTodaysDismissal() = runTest {
        repository.dismissBriefing("2026-10-15")
        repository.setDiscoverySection(DiscoverySection.DAILY_BRIEF, false)
        assertEquals("2026-10-15", repository.briefingDismissedDate.first())
    }

    @Test
    fun withAndIsOnAgreeForEverySection() {
        DiscoverySection.entries.forEach { section ->
            assertFalse(DiscoverySections().with(section, false).isOn(section))
            DiscoverySection.entries.filter { it != section }.forEach { other ->
                assertTrue(DiscoverySections().with(section, false).isOn(other))
            }
        }
    }
}
