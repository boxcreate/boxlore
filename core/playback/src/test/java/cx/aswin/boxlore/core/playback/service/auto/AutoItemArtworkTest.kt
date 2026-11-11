package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.playback.PlaybackArtworkMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A player item's cover has to work in the car (an in-app link) and on the phone (its real address). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoItemArtworkTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AutoArtworkSourceStore.clearMemoryForTests()
    }

    private fun item(artwork: String?, sources: List<String>? = null): MediaItem {
        val extras = Bundle().apply {
            putString("kept", "yes")
            sources?.let { putStringArrayList(PlaybackArtworkMetadata.SOURCES, ArrayList(it)) }
        }
        val metadata = MediaMetadata.Builder().setTitle("Episode").setArtworkUri(artwork?.let(Uri::parse)).setExtras(extras).build()
        return MediaItem.Builder().setMediaId("42").setUri("https://cdn.example/audio.mp3").setMediaMetadata(metadata).build()
    }

    private fun MediaItem.sources(): List<String> = mediaMetadata.extras?.getStringArrayList(PlaybackArtworkMetadata.SOURCES).orEmpty()

    private fun MediaItem.isCarLink(): Boolean =
        mediaMetadata.artworkUri?.scheme == "content" && mediaMetadata.artworkUri?.authority == "${context.packageName}.collage"

    @Test
    fun aWebCoverBecomesALinkTheCarCanOpenAndKeepsItsAddress() {
        val swapped = AutoItemArtwork.forCar(context, item("https://cdn.example/episode.jpg"))

        assertEquals(true, swapped.isCarLink())
        assertEquals(listOf("https://cdn.example/episode.jpg"), swapped.sources())
        assertEquals("https://cdn.example/episode.jpg", PlaybackArtworkMetadata.savedArtworkUrl(swapped.mediaMetadata))
        assertEquals("42", swapped.mediaId)
        assertEquals("yes", swapped.mediaMetadata.extras?.getString("kept"))
    }

    @Test
    fun theShowCoverStandsInWhenTheEpisodeImageFails() {
        val swapped = AutoItemArtwork.forCar(
            context,
            item("https://cdn.example/episode.jpg", sources = listOf("https://cdn.example/episode.jpg", "https://cdn.example/show.jpg")),
        )

        assertEquals(
            listOf("https://cdn.example/episode.jpg", "https://cdn.example/show.jpg"),
            AutoArtworkRepository.originalsOf(context, swapped.mediaMetadata.artworkUri),
        )
    }

    @Test
    fun savedSourcesKeepTheirOrder() {
        val saved = listOf("https://cdn.example/episode.jpg", "https://cdn.example/show.jpg")

        val showCoverShown = AutoItemArtwork.forCar(context, item("https://cdn.example/show.jpg", sources = saved))
        val otherCoverShown = AutoItemArtwork.forCar(context, item("https://cdn.example/other.jpg", sources = saved))

        assertEquals(saved, showCoverShown.sources())
        assertEquals(saved + "https://cdn.example/other.jpg", otherCoverShown.sources())
    }

    @Test
    fun anItemWithOnlySavedSourcesGetsItsCoverFromThem() {
        val swapped = AutoItemArtwork.forCar(context, item(artwork = null, sources = listOf("https://cdn.example/show.jpg")))

        assertEquals(true, swapped.isCarLink())
        assertEquals(listOf("https://cdn.example/show.jpg"), swapped.sources())
    }

    @Test
    fun anItemTheCarCanAlreadyShowOrWithNoCoverIsLeftAsItIs() {
        val carLink = item(AutoArtworkRepository.remoteUri(context, "https://cdn.example/episode.jpg").toString())
        val icon = item("android.resource://${context.packageName}/drawable/ic_auto_episode")
        val bare = item(artwork = null)

        assertSame(carLink, AutoItemArtwork.forCar(context, carLink))
        assertSame(icon, AutoItemArtwork.forCar(context, icon))
        assertSame(bare, AutoItemArtwork.forCar(context, bare))
    }

    @Test
    fun anInAppCoverLinkGetsItsRealAddressesSaved() {
        val link = AutoArtworkRepository.remoteUri(context, "https://cdn.example/episode.jpg", "https://cdn.example/show.jpg")

        val saved = AutoItemArtwork.withSources(context, item(link.toString()))

        assertEquals(listOf("https://cdn.example/episode.jpg", "https://cdn.example/show.jpg"), saved.sources())
        assertEquals(link, saved.mediaMetadata.artworkUri)
        assertEquals("https://cdn.example/episode.jpg", PlaybackArtworkMetadata.savedArtworkUrl(saved.mediaMetadata))
    }

    @Test
    fun sourcesAlreadySavedOrACoverThatIsNotOursAreLeftAsTheyAre() {
        val link = AutoArtworkRepository.remoteUri(context, "https://cdn.example/episode.jpg").toString()
        val alreadySaved = item(link, sources = listOf("https://cdn.example/original.jpg"))
        val web = item("https://cdn.example/episode.jpg")
        val collage = item("content://${context.packageName}.collage/collage/home.png")
        val unknownLink = item("content://${context.packageName}.collage/art/${"0".repeat(64)}")

        assertSame(alreadySaved, AutoItemArtwork.withSources(context, alreadySaved))
        assertSame(web, AutoItemArtwork.withSources(context, web))
        assertSame(collage, AutoItemArtwork.withSources(context, collage))
        assertSame(unknownLink, AutoItemArtwork.withSources(context, unknownLink))
    }
}
