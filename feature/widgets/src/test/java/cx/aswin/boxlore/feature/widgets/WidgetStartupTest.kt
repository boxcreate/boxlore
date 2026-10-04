package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WidgetStartupTest {
    @Test
    fun unusedPlaybackWidgetsInstallPortsWithoutRestoringPlayback() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val deps = object : NowPlayingWidgetDependencies {
            override val context = context
            override val scope: CoroutineScope = backgroundScope
            override val playback: WidgetPlaybackSource
                get() = error("Unused widgets must not construct or restore playback")
        }
        configureNowPlayingWidget(deps)
        runCurrent()
        assertSame(deps, NowPlayingWidgetDependenciesHolder.require())
    }

    @Test
    fun unusedLibraryWidgetsInstallPortsWithoutRankingLibrary() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val deps = object : LibraryWidgetDependencies {
            override val context = context
            override val scope: CoroutineScope = backgroundScope
            override val library: WidgetLibrarySource
                get() = error("Unused widgets must not construct the library projection")
        }
        configureLibraryWidgets(deps)
        runCurrent()
        assertSame(deps, LibraryWidgetDependenciesHolder.require())
    }

    @Test
    fun installedWidgetsOnlyActivateTheirOwnFamily() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val manager = context.getSystemService(AppWidgetManager::class.java)
        Shadows.shadowOf(manager).bindAppWidgetId(42, ComponentName(context, PlaybackNextControlsWidgetReceiver::class.java))
        assertTrue(hasInstalledWidgets(context, WidgetProviders.all.map { it.receiverClass }))
        assertFalse(hasInstalledWidgets(context, LibraryWidgetProviders.all.map { it.receiverClass }))
        Shadows.shadowOf(manager).bindAppWidgetId(43, ComponentName(context, NewEpisodesWidgetReceiver::class.java))
        assertTrue(hasInstalledWidgets(context, LibraryWidgetProviders.all.map { it.receiverClass }))
    }
}
