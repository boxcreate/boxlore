package cx.aswin.boxlore.navigation

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.currentBackStackEntryAsState
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkColorsEnabled
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkLoaderColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationBaseColors
import cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationColors
import cx.aswin.boxlore.core.designsystem.theme.rememberAnimatedArtworkColors

/** Navigation owns only temporary visuals; route actions and transition geometry stay unchanged. */
@Composable
internal fun ArtworkNavigationHostTheme(navController: NavHostController, content: @Composable (Modifier) -> Unit) {
    val palettes = LocalArtworkNavigationColors.current
    val base = MaterialTheme.colorScheme
    val enabled = LocalArtworkColorsEnabled.current
    val currentBase by rememberUpdatedState(base)
    val currentEnabled by rememberUpdatedState(enabled)
    DisposableEffect(navController, palettes) {
        val listener = NavController.OnDestinationChangedListener { controller, _, _ ->
            controller.currentBackStackEntry?.id?.let { palettes?.beginEntry(it, currentBase, currentEnabled) }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
            palettes?.previewAppTheme(null)
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    val visibleEntries by navController.visibleEntries.collectAsState()
    val transitioning = visibleEntries.count { it.destination is ComposeNavigator.Destination } > 1
    val previewAppEntry = rememberAppThemePreviewEntry(navController, entry, transitioning)
    val initial = if (enabled) palettes?.initialColorsFor(entry?.id) ?: base else base
    val handoffKey = entry?.id to palettes?.handoffKeyFor(entry?.id)
    val colors = rememberAnimatedArtworkColors(if (transitioning) initial else base, initialColors = initial, animationKey = handoffKey)
    val isDetail = isArtworkDetailRoute(entry?.destination?.route)
    SideEffect {
        palettes?.previewAppTheme(previewAppEntry)
        // Detail pages publish their own clock, including the entire loading phase.
        if (!isDetail) entry?.id?.let { palettes?.update(it, base) }
    }
    val backdrop = if (enabled && previewAppEntry == null) {
        artworkNavigationBackdrop(palettes, entry?.id, transitioning, isDetail, base.background, initial.background)
    } else {
        base.background
    }
    CompositionLocalProvider(LocalArtworkNavigationBaseColors provides base, LocalArtworkLoaderColors provides colors) {
        content(Modifier.background(backdrop))
    }
}

private fun artworkNavigationBackdrop(
    palettes: cx.aswin.boxlore.core.designsystem.theme.ArtworkNavigationColors?,
    entryId: String?,
    transitioning: Boolean,
    isDetail: Boolean,
    base: androidx.compose.ui.graphics.Color,
    initial: androidx.compose.ui.graphics.Color,
): androidx.compose.ui.graphics.Color = when {
    transitioning -> palettes?.transitionColors?.background ?: initial
    isDetail -> palettes?.colorsFor(entryId)?.background ?: initial
    else -> base
}

/** Native Back starts the previous page's lifecycle before changing the current entry. */
@Composable
private fun rememberAppThemePreviewEntry(navController: NavHostController, entry: NavBackStackEntry?, transitioning: Boolean): String? {
    var settledEntryId by remember(navController) { mutableStateOf<String?>(null) }
    val previous = navController.previousBackStackEntry
    val entryLifecycle = entry?.lifecycle?.currentStateFlow?.collectAsState()?.value
    val previousLifecycle = previous?.lifecycle?.currentStateFlow?.collectAsState()?.value
    SideEffect {
        if (!transitioning && entryLifecycle == Lifecycle.State.RESUMED) settledEntryId = entry?.id
    }
    // visibleEntries can still contain only the source during a native preview.
    // Remembering the resumed source also excludes an ordinary forward slide.
    if (entry?.id != settledEntryId || !isArtworkDetailRoute(entry?.destination?.route)) return null
    return previous?.takeIf {
        previousLifecycle?.isAtLeast(Lifecycle.State.STARTED) == true && !isArtworkDetailRoute(it.destination.route)
    }?.id
}

internal fun isArtworkDetailRoute(route: String?): Boolean = route?.let { it.startsWith("podcast/") || it.startsWith("episode/") } == true
