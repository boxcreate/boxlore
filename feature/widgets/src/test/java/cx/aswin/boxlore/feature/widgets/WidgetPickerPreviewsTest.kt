package cx.aswin.boxlore.feature.widgets

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.prefs.FontRoundnessAxis
import cx.aswin.boxlore.core.prefs.WidgetAppearance
import cx.aswin.boxlore.core.prefs.WidgetShowsArtwork
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetPickerPreviewsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun everyWidgetInThePickerHasItsOwnLivePreview() {
        val previews = WidgetPickerPreviews.previews()
        val receivers = WidgetProviders.all.map { it.receiverClass } + LibraryWidgetProviders.all.map { it.receiverClass }

        assertEquals(receivers.toSet(), previews.map { it.receiver }.toSet())
        assertEquals(previews.size, previews.map { it.key }.toSet().size)
    }

    @Test
    fun everyLivePreviewBuildsAndAppliesLikeAPlacedWidget() {
        useTheme(brand = "violet")
        for (preview in WidgetPickerPreviews.previews()) {
            preview.build(context).apply(context, FrameLayout(context))
        }
    }

    @Test
    fun sampleContentHasCoversSoPreviewsNeverShowPlaceholders() {
        val playback = WidgetPickerPreviews.samplePlayback(context)
        val library = WidgetPickerPreviews.sampleLibrary(context)
        val covers = listOfNotNull(playback.artworkCachePath) + library.subscriptions.mapNotNull { it.artworkCachePath } + library.newEpisodes.mapNotNull { it.artworkCachePath }

        assertEquals(1 + library.subscriptions.size + library.newEpisodes.size, covers.size)
        assertTrue(covers.all { File(it).length() > 0L })
        assertTrue("several different covers", covers.toSet().size >= 4)
        assertTrue(library.subscriptions.any { it.isNew } && library.subscriptions.any { !it.isNew })
        // The badge shows the library's size, not the handful of sample tiles.
        assertTrue(library.count(LibraryWidgetKind.SUBSCRIPTIONS) > library.subscriptions.size)
    }

    @Test
    fun sampleCoversBuiltByManyPreviewsAtOnceAreWholeFilesWithNothingLeftHalfWritten() = runTest {
        val covers = File(context.cacheDir, WidgetArtworkLoader.CACHE_DIR_NAME)
        covers.deleteRecursively()

        // Settings draws every preview at the same time, and they share covers.
        withContext(Dispatchers.Default) {
            List(12) { async { WidgetPickerPreviews.samplePlayback(context) to WidgetPickerPreviews.sampleLibrary(context) } }.awaitAll()
        }

        val files = covers.listFiles().orEmpty()
        assertTrue("covers were written", files.isNotEmpty())
        assertTrue("no unfinished file is left behind: ${files.map { it.name }}", files.none { it.name.endsWith(".tmp") })
        assertTrue("every cover decodes", files.all { BitmapFactory.decodeFile(it.absolutePath) != null })
    }

    @Test
    fun thePreviewLookChangesWithTheColoursAndTheCoverStyleAndOnlyThen() {
        useTheme(brand = "violet")
        val violet = WidgetPickerPreviews.lookSignature(context)
        assertEquals("an unchanged look publishes nothing new", violet, WidgetPickerPreviews.lookSignature(context))

        useTheme(brand = "emerald")
        val emerald = WidgetPickerPreviews.lookSignature(context)
        assertNotEquals(violet, emerald)

        useTheme(brand = "emerald", appearance = WidgetAppearance.SYSTEM)
        assertNotEquals(emerald, WidgetPickerPreviews.lookSignature(context))

        useTheme(brand = "emerald", showsArtwork = WidgetShowsArtwork.SHAPES)
        assertNotEquals(emerald, WidgetPickerPreviews.lookSignature(context))
    }

    @Test
    fun beforeAndroid15NothingIsPublishedAndTheStaticPreviewsStay() = runTest {
        WidgetPickerPreviews.refresh(context)
        assertTrue(context.getSharedPreferences(WidgetPickerPreviews.PREFS_NAME, Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test
    @Config(sdk = [35])
    fun onAndroid15PublishingNeverThrowsAndARejectedPreviewIsTriedAgain() = runTest {
        useTheme(brand = "violet")
        WidgetPickerPreviews.refresh(context)
        val published = context.getSharedPreferences(WidgetPickerPreviews.PREFS_NAME, Context.MODE_PRIVATE).all
        // Only previews the system accepted are remembered; the rest stay due for the next call.
        assertTrue(published.values.all { it == WidgetPickerPreviews.lookSignature(context) })
    }

    private fun useTheme(
        brand: String,
        appearance: String = WidgetAppearance.APP,
        showsArtwork: String = WidgetShowsArtwork.ROUNDED,
    ) {
        context
            .getSharedPreferences(FontRoundnessAxis.THEME_FAST_CACHE, Context.MODE_PRIVATE)
            .edit()
            .putString("theme_brand", brand)
            .putString("theme_config", "dark")
            .putString("surface_style", "classic_dynamic")
            .putBoolean("use_dynamic_color", false)
            .putString(WidgetAppearance.PREF_KEY, appearance)
            .putString(WidgetShowsArtwork.PREF_KEY, showsArtwork)
            .commit()
    }
}
