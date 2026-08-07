package cx.aswin.boxlore.feature.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

abstract class BaseLibraryWidgetReceiver : AppWidgetProvider() {
    protected abstract val kind: LibraryWidgetKind

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val appContext = context.applicationContext
        // Prefs read off main; single render (no duplicate requestRefresh pass).
        receiverScope.launch {
            val snapshot = LibraryWidgetSnapshotStore(appContext).read() ?: LibraryWidgetSnapshot()
            LibraryWidgetRenderer.updateAll(
                context = appContext,
                appWidgetManager = appWidgetManager,
                appWidgetIds = appWidgetIds,
                snapshot = snapshot,
                kind = kind,
            )
        }
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_ENABLED) {
            receiverScope.launch {
                runCatching { LibraryWidgetDependenciesHolder.require() }
                    .onSuccess { deps -> LibraryWidgetCoordinator.start(deps) }
            }
        }
    }

    companion object {
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

class SubscriptionsWidgetReceiver : BaseLibraryWidgetReceiver() {
    override val kind = LibraryWidgetKind.SUBSCRIPTIONS
}

class NewEpisodesWidgetReceiver : BaseLibraryWidgetReceiver() {
    override val kind = LibraryWidgetKind.NEW_EPISODES
}

object LibraryWidgetProviders {
    data class Provider(
        val receiverClass: Class<out AppWidgetProvider>,
        val kind: LibraryWidgetKind,
    )

    val all =
        listOf(
            Provider(SubscriptionsWidgetReceiver::class.java, LibraryWidgetKind.SUBSCRIPTIONS),
            Provider(NewEpisodesWidgetReceiver::class.java, LibraryWidgetKind.NEW_EPISODES),
        )
}
