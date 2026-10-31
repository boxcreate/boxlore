package cx.aswin.boxlore.feature.info

import android.Manifest
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.feature.info.components.areAppNotificationsEnabled

/** The show page's scroll-linked header and its floating title, like Episode Info's. */
internal data class ShowPageTitleMotion(
    val scrollOffset: Float,
    /** 0 while the header is expanded, 1 once it has collapsed. */
    val scrollFraction: Float,
    val keyboardVisible: Boolean,
    val statusBarHeight: Dp,
    val collapsedHeaderHeight: Dp,
    /** Transparent over the artwork, then the container colour as the header collapses. */
    val headerColor: Color,
    val titleFontSize: TextUnit,
    /** From below the header (above the hero) up into the header. */
    val titleTranslationY: Float,
    val titleMaxLines: Int,
    /** Hidden until the header has nearly collapsed, then fading in. */
    val titleAlpha: Float,
    val titleStartPadding: Dp,
    val titleEndPadding: Dp,
)

@Composable
internal fun rememberShowPageTitleMotion(listState: LazyListState): ShowPageTitleMotion {
    val scrollOffset by remember {
        derivedStateOf {
            // Fully collapsed once the first item has scrolled away.
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 1000f
        }
    }
    val density = LocalDensity.current
    val scrollFraction = (scrollOffset / with(density) { 150.dp.toPx() }).coerceIn(0f, 1f)
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val collapsedHeaderHeight = 64.dp + statusBarHeight
    // Don't lerp from Color.Transparent: its RGB is 0,0,0, which flashes black.
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainer
    val headerColor by animateColorAsState(
        targetValue = surfaceColor.copy(alpha = scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "headerColor",
    )
    val bodyTitleYPx = with(density) { collapsedHeaderHeight.toPx() + 16.dp.toPx() }
    val headerTitleYPx = with(density) { (statusBarHeight + 18.dp).toPx() }
    val titleTranslationY by animateFloatAsState(
        targetValue = androidx.compose.ui.util.lerp(bodyTitleYPx, headerTitleYPx, scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessMedium, dampingRatio = 0.85f),
        label = "titleY",
    )
    val titleStartPadding by animateDpAsState(
        targetValue = lerp(20.dp, 56.dp, scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "titleStartPadding",
    )
    val titleEndPadding by animateDpAsState(
        targetValue = lerp(20.dp, 112.dp, scrollFraction),
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "titleEndPadding",
    )
    return ShowPageTitleMotion(
        scrollOffset = scrollOffset,
        scrollFraction = scrollFraction,
        keyboardVisible = WindowInsets.ime.getBottom(density) > 0,
        statusBarHeight = statusBarHeight,
        collapsedHeaderHeight = collapsedHeaderHeight,
        headerColor = headerColor,
        titleFontSize = androidx.compose.ui.unit.lerp(
            MaterialTheme.typography.headlineSmall.fontSize,
            MaterialTheme.typography.titleMedium.fontSize,
            scrollFraction,
        ),
        titleTranslationY = titleTranslationY,
        titleMaxLines = 1,
        titleAlpha = if (scrollFraction > 0.8f) (scrollFraction - 0.8f) / 0.2f else 0f,
        titleStartPadding = titleStartPadding,
        titleEndPadding = titleEndPadding,
    )
}

/**
 * The system notification permission as the show page sees it: whether the system blocks the
 * app's notifications, and the alert change waiting on the permission. A grant, or returning
 * with notifications allowed, runs the waiting change.
 */
internal class ShowNotificationPermission(blocked: Boolean) {
    var systemBlocked by mutableStateOf(blocked)
    var pendingAction by mutableStateOf<(() -> Unit)?>(null)
    internal var launcher: ManagedActivityResultLauncher<String, Boolean>? = null

    /** Asks for Android 13's notification permission. */
    fun request() {
        launcher?.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    internal fun runPending() {
        pendingAction?.invoke()
        pendingAction = null
    }
}

/**
 * [onAllowed] runs when the listener grants the permission (true) or comes back to the page with
 * notifications allowed (false); [onDenied] when they turn the request down.
 */
@Composable
internal fun rememberShowNotificationPermission(
    podcastId: String,
    onAllowed: (granted: Boolean) -> Unit,
    onDenied: () -> Unit,
): ShowNotificationPermission {
    val context = LocalContext.current
    val allowed by rememberUpdatedState(onAllowed)
    val denied by rememberUpdatedState(onDenied)
    val permission = remember(podcastId) { ShowNotificationPermission(!areAppNotificationsEnabled(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permission.systemBlocked = !areAppNotificationsEnabled(context)
                if (!permission.systemBlocked) {
                    allowed(false)
                    permission.runPending()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    permission.launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        BoxcastPrefs(context).setHasRequestedNotificationPermission(true)
        permission.systemBlocked = !isGranted
        if (isGranted) {
            allowed(true)
            permission.runPending()
        } else {
            denied()
            permission.pendingAction = null
        }
    }
    return permission
}

/** The people named in the show's recent episodes, hosts, creators and presenters first, then by name. */
internal fun sortedShowPersons(episodes: List<Episode>): List<Person> = episodes
    .take(15)
    .flatMap { it.persons ?: emptyList() }
    .distinctBy { it.name.lowercase().trim() }
    .sortedWith(
        compareByDescending<Person> {
            val role = it.role?.lowercase() ?: ""
            role.contains("host") || role.contains("creator") || role.contains("presenter")
        }.thenBy { it.name },
    )
