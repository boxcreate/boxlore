package cx.aswin.boxlore.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.window.layout.FoldingFeature
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout

/** Adaptive's collector owns the single WindowInfoTracker subscription for this app shell. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberBoxloreWindowLayout(): BoxloreWindowLayout {
    val size = currentWindowDpSize()
    val features by collectFoldingFeaturesAsState()
    val density = LocalDensity.current.density
    val configuration = LocalConfiguration.current
    return remember(size, features, density, configuration.screenWidthDp, configuration.screenHeightDp) {
        BoxloreWindowLayout(
            widthDp = size.width.value,
            heightDp = size.height.value,
            folds = features.map { boxloreFold(it, density) },
            configurationWidthDp = configuration.screenWidthDp,
            configurationHeightDp = configuration.screenHeightDp,
        )
    }
}

internal fun boxloreFold(feature: FoldingFeature, density: Float): BoxloreFold {
    require(density.isFinite() && density > 0f)
    return BoxloreFold(
        state = if (feature.state == FoldingFeature.State.HALF_OPENED) BoxloreFoldState.HalfOpened else BoxloreFoldState.Flat,
        orientation = if (feature.orientation == FoldingFeature.Orientation.HORIZONTAL) BoxloreFoldOrientation.Horizontal else BoxloreFoldOrientation.Vertical,
        bounds = feature.bounds.let { BoxloreLayoutBounds(it.left / density, it.top / density, it.right / density, it.bottom / density) },
        isSeparating = feature.isSeparating,
        isOccluding = feature.occlusionType == FoldingFeature.OcclusionType.FULL,
    )
}
