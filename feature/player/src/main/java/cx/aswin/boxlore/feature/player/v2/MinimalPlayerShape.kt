package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.rectangle

internal enum class MinimalControlKind { PLAY, BACK, FORWARD, SLEEP, SPEED }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun rememberMinimalControlShape(kind: MinimalControlKind, active: Boolean): Shape {
    val morph = remember(kind) {
        val target = when (kind) {
            MinimalControlKind.PLAY -> MaterialShapes.Circle
            MinimalControlKind.BACK -> MaterialShapes.PuffyDiamond
            MinimalControlKind.FORWARD -> MaterialShapes.Cookie9Sided
            MinimalControlKind.SLEEP -> MaterialShapes.Cookie4Sided
            MinimalControlKind.SPEED -> MaterialShapes.Cookie6Sided
        }
        val resting = RoundedPolygon.rectangle(width = 1f, height = 1f, rounding = CornerRounding(0.28f))
        Morph(resting.normalized(), target.normalized())
    }
    val progress by animateFloatAsState(if (active) 1f else 0f, spring(dampingRatio = 1f, stiffness = 550f), label = "minimalControlShape")
    return remember(morph, progress) { MinimalControlMorphShape(morph, progress) }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private class MinimalControlMorphShape(morph: Morph, progress: Float) : Shape {
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
