package cx.aswin.boxlore.wear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cx.aswin.boxlore.wear.ui.WatchApp
import cx.aswin.boxlore.wear.ui.WatchRoutes
import cx.aswin.boxlore.wear.ui.theme.WatchTheme

class MainActivity : ComponentActivity() {
    /** The screen a launch asked for, such as the player from a tile; cleared once shown. */
    private var requestedRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as WatchApplication).container
        if (savedInstanceState == null) requestedRoute = WatchRoutes.fromLaunch(intent)
        setContent {
            WatchTheme {
                WatchApp(
                    container = container,
                    requestedRoute = requestedRoute,
                    onRouteShown = { requestedRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        WatchRoutes.fromLaunch(intent)?.let { requestedRoute = it }
    }
}
