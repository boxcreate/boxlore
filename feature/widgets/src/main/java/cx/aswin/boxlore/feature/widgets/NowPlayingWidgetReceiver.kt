package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseNowPlayingWidgetReceiver : AppWidgetProvider() {
    protected abstract val variant: WidgetVariant

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val appContext = context.applicationContext
        // Prefs read off main; single render (no duplicate requestRefresh pass).
        receiverScope.launch {
            val store = NowPlayingWidgetSnapshotStore(appContext)
            val snapshot = store.read() ?: NowPlayingWidgetSnapshot()
            val optionsById =
                appWidgetIds.associateWith { id -> appWidgetManager.getAppWidgetOptions(id) }
            NowPlayingWidgetRenderer.updateAll(
                context = appContext,
                appWidgetManager = appWidgetManager,
                appWidgetIds = appWidgetIds,
                snapshot = snapshot,
                optionsById = optionsById,
                variant = variant,
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        val appContext = context.applicationContext
        receiverScope.launch {
            val snapshot = NowPlayingWidgetSnapshotStore(appContext).read() ?: NowPlayingWidgetSnapshot()
            NowPlayingWidgetRenderer.updateOne(
                context = appContext,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                options = newOptions,
                variant = variant,
            )
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_ENABLED -> {
                receiverScope.launch {
                    runCatching { NowPlayingWidgetDependenciesHolder.require() }
                        .onSuccess { deps ->
                            NowPlayingWidgetCoordinator.start(deps)
                        }
                }
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

class NowPlayingWidgetReceiver : BaseNowPlayingWidgetReceiver() {
    override val variant = WidgetVariant.NOW_PLAYING
}

class NowPlayingBarWidgetReceiver : BaseNowPlayingWidgetReceiver() {
    override val variant = WidgetVariant.BAR
}

class PlaybackControlsWidgetReceiver : BaseNowPlayingWidgetReceiver() {
    override val variant = WidgetVariant.CONTROLS
}

class PlaybackNextControlsWidgetReceiver : BaseNowPlayingWidgetReceiver() {
    override val variant = WidgetVariant.CONTROLS_NEXT
}

object WidgetProviders {
    data class Provider(
        val receiverClass: Class<out AppWidgetProvider>,
        val variant: WidgetVariant,
    )

    val all =
        listOf(
            Provider(NowPlayingWidgetReceiver::class.java, WidgetVariant.NOW_PLAYING),
            Provider(NowPlayingBarWidgetReceiver::class.java, WidgetVariant.BAR),
            Provider(PlaybackControlsWidgetReceiver::class.java, WidgetVariant.CONTROLS),
            Provider(PlaybackNextControlsWidgetReceiver::class.java, WidgetVariant.CONTROLS_NEXT),
        )
}
