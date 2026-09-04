package cx.aswin.boxlore.navigation

import android.app.Application
import android.os.Bundle
import androidx.navigation.NavType
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EpisodeNavRouteRegressionTest {

    @Test
    fun encodeNavArg_handlesNullEmptyAndBlank() {
        assertEquals("_", encodeNavArg(null))
        assertEquals("_", encodeNavArg(""))
        assertEquals("_", encodeNavArg("   "))
        assertEquals("_", encodeNavArg("\t\n"))
        assertEquals("_", encodeNavArg("_"))
    }

    @Test
    fun encodeNavArg_encodesSpecialCharactersAndEmojis() {
        val encoded = encodeNavArg("Q&A: Tech / AI? #1 & <cool> 🎙️")
        assertTrue(encoded.contains("%20"))
        assertTrue(encoded.contains("%26"))
        assertTrue(encoded.contains("%2F"))
        assertTrue(encoded.contains("%3F"))
    }

    @Test
    fun decodeNavArg_handlesNullEmptyBlankAndPlaceholder() {
        assertEquals("", decodeNavArg(null))
        assertEquals("", decodeNavArg(""))
        assertEquals("", decodeNavArg("   "))
        assertEquals("", decodeNavArg("\t\n"))
        assertEquals("", decodeNavArg("_"))
        assertEquals("", decodeNavArg(" _ "))
        assertEquals("", decodeNavArg("%20%20"))
        assertEquals("", decodeNavArg("%20_%20"))
    }

    @Test
    fun decodeNavArg_decodesNormalAndSpecialCharacters() {
        assertEquals("Hello World", decodeNavArg("Hello World"))
        assertEquals("Hello World", decodeNavArg("Hello%20World"))
        val special = "Q&A: Tech / AI? #1 & <cool> 🎙️"
        val encoded = encodeNavArg(special)
        assertEquals(special, decodeNavArg(encoded))
    }

    @Test
    fun decodeNavArg_preservesUnderscoresInText() {
        assertEquals("episode_123_final", decodeNavArg("episode_123_final"))
        assertEquals("_leading", decodeNavArg("_leading"))
        assertEquals("trailing_", decodeNavArg("trailing_"))
    }

    @Test
    fun decodeNavArg_handlesMalformedPercentEncodingWithoutCrashing() {
        val r1 = decodeNavArg("%ZZ")
        val r2 = decodeNavArg("%")
        val r3 = decodeNavArg("%2")
        val r4 = decodeNavArg("%2G")
        // Verify all malformed inputs are handled gracefully without throwing and return non-empty strings
        assertTrue(r1.isNotEmpty())
        assertTrue(r2.isNotEmpty())
        assertTrue(r3.isNotEmpty())
        assertTrue(r4.isNotEmpty())
    }

    @Test
    fun episodeFullPathRoute_buildsSafeRouteWithNullAndBlankDescription() {
        val routeNullDesc = episodeFullPathRoute(
            episodeId = "ep_123",
            title = "Episode Title",
            description = null,
            imageUrl = "https://example.com/img.jpg",
            audioUrl = "https://example.com/audio.mp3",
            duration = 3600,
            podcastId = "pod_456",
            podcastTitle = "Podcast Title",
        )
        assertTrue(routeNullDesc.startsWith("episode/ep_123/Episode%20Title/_/"))
        assertTrue(routeNullDesc.endsWith("/pod_456/Podcast%20Title"))

        val routeBlankDesc = episodeFullPathRoute(
            episodeId = "ep_123",
            title = "Episode Title",
            description = "    ",
            imageUrl = "https://example.com/img.jpg",
            audioUrl = "https://example.com/audio.mp3",
            duration = 3600,
            podcastId = "pod_456",
            podcastTitle = "Podcast Title",
        )
        assertTrue(routeBlankDesc.startsWith("episode/ep_123/Episode%20Title/_/"))
    }

    @Test
    fun episodeFullPathRoute_truncatesDescriptionAt500Characters() {
        val longDesc = "a".repeat(600)
        val route = episodeFullPathRoute(
            episodeId = "ep_123",
            title = "Title",
            description = longDesc,
            imageUrl = "img",
            audioUrl = "audio",
            duration = 60,
            podcastId = "pod",
            podcastTitle = "Pod",
        )
        val expectedSegment = "a".repeat(500)
        assertTrue(route.contains("/$expectedSegment/"))
    }

    @Test
    fun episodeFullPathRoute_handlesSpecialCharactersInAllFields() {
        val route = episodeFullPathRoute(
            episodeId = "ep?123/special",
            title = "Q&A / Discussion #42",
            description = "Special chars: <>&\"'/?# with emoji 🚀",
            imageUrl = "https://example.com/art?size=large&format=png",
            audioUrl = "https://example.com/ep.mp3?token=abc#frag",
            duration = 1800,
            podcastId = "pod/123?q=boxlore",
            podcastTitle = "The & Podcast",
            querySuffix = "?entryPoint=home_feed",
        )
        assertTrue(route.contains("?entryPoint=home_feed"))
        assertTrue(!route.contains(" <>&\"'/?# "))
    }

    @Test
    fun episodeDescriptionNavArgument_isNullableWithNullDefaultValue() {
        val arg = navArgument("episodeDescription") {
            type = NavType.StringType
            nullable = true
            defaultValue = null
        }
        val navArgument = arg.argument
        assertTrue("NavArgument must be nullable", navArgument.isNullable)
        assertTrue("Default value must be present", navArgument.isDefaultValuePresent)
        assertNull("Default value must be null", navArgument.defaultValue)
        assertEquals(NavType.StringType, navArgument.type)

        // Verify that putting default value into an empty bundle does not throw IllegalArgumentException
        val bundle = Bundle()
        navArgument.putDefaultValue("episodeDescription", bundle)
        assertNull(bundle.getString("episodeDescription"))
        assertEquals("", decodeNavArg(bundle.getString("episodeDescription")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun episodeDescription_nonNullable_reproducesCrashlyticsIllegalArgumentException() {
        // Replicates Firebase Crashlytics issue 4e2e3de54ffcfd45eacd858aa62bbcb9
        val destination = object : androidx.navigation.NavDestination("testNavigator") {}.apply {
            addArgument(
                "episodeDescription",
                navArgument("episodeDescription") {
                    type = NavType.StringType
                    nullable = false
                }.argument,
            )
        }
        val bundleWithNull = Bundle().apply { putString("episodeDescription", null) }
        destination.addInDefaultArgs(bundleWithNull)
    }

    @Test
    fun episodeDescription_nullableWithDefaultNull_allowsNullInBundleWithoutException() {
        val destination = object : androidx.navigation.NavDestination("testNavigator") {}.apply {
            addArgument(
                "episodeDescription",
                navArgument("episodeDescription") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }.argument,
            )
        }
        val bundleWithNull = Bundle().apply { putString("episodeDescription", null) }
        val mergedBundle = destination.addInDefaultArgs(bundleWithNull)
        assertNull(mergedBundle?.getString("episodeDescription"))
        assertEquals("", decodeNavArg(mergedBundle?.getString("episodeDescription")))

        val emptyBundle = Bundle()
        val mergedEmpty = destination.addInDefaultArgs(emptyBundle)
        assertNull(mergedEmpty?.getString("episodeDescription"))
        assertEquals("", decodeNavArg(mergedEmpty?.getString("episodeDescription")))
    }
}
