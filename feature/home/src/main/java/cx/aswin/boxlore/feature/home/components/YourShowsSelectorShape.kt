package cx.aswin.boxlore.feature.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph

/** Varied silhouettes remain attached to show identity across taps, pinning and recomposition. */
internal enum class YourShowsSelectorSilhouette { COOKIE_FOUR, COOKIE_SIX, COOKIE_NINE, PUFFY }

internal fun selectorSilhouette(identity: String): YourShowsSelectorSilhouette =
    YourShowsSelectorSilhouette.entries[Math.floorMod(identity.hashCode(), YourShowsSelectorSilhouette.entries.size)]

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberYourShowsSelectorShape(identity: String, selected: Boolean): Shape {
    val silhouette = remember(identity) { selectorSilhouette(identity) }
    val morph = remember(silhouette) {
        val target = when (silhouette) {
            YourShowsSelectorSilhouette.COOKIE_FOUR -> MaterialShapes.Cookie4Sided
            YourShowsSelectorSilhouette.COOKIE_SIX -> MaterialShapes.Cookie6Sided
            YourShowsSelectorSilhouette.COOKIE_NINE -> MaterialShapes.Cookie9Sided
            YourShowsSelectorSilhouette.PUFFY -> MaterialShapes.Puffy
        }
        Morph(MaterialShapes.Square.normalized(), target.normalized())
    }
    val progress by animateFloatAsState(if (selected) 1f else 0f, YourShowsMotion.spatial(), label = "show_selector_shape")
    return remember(morph, progress) { SelectorMorphShape(morph, progress) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal class SelectorMorphShape(morph: Morph, progress: Float) : Shape {
    private val source = morph.toPath(progress.coerceIn(0f, 1f))
    private var cachedSize: Size? = null
    private var cachedOutline: Outline? = null

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        if (cachedSize == size) return checkNotNull(cachedOutline)
        val path = Path().apply {
            addPath(source)
            transform(Matrix().apply { scale(size.width, size.height) })
            translate(Offset(size.width / 2f, size.height / 2f) - getBounds().center)
        }
        return Outline.Generic(path).also {
            cachedSize = size
            cachedOutline = it
        }
    }
}
