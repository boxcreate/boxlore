package cx.aswin.boxlore.feature.home.components

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.drawOutline

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.homeMixBackdrop(enabled: Boolean): Modifier {
    if (!enabled) return this
    val color = MaterialTheme.colorScheme.tertiary
    val upperShape = MaterialShapes.Puffy.toShape()
    val lowerShape = MaterialShapes.Cookie6Sided.toShape()
    return drawWithCache {
        val upperSize = 180.dp.toPx()
        val lowerSize = 210.dp.toPx()
        val upperOutline = upperShape.createOutline(Size(upperSize, upperSize), layoutDirection, this)
        val lowerOutline = lowerShape.createOutline(Size(lowerSize, lowerSize), layoutDirection, this)
        onDrawBehind {
            clipRect {
                scale(scaleX = if (layoutDirection == LayoutDirection.Rtl) -1f else 1f, scaleY = 1f) {
                    translate(left = size.width - 112.dp.toPx(), top = -80.dp.toPx()) {
                        rotate(12f, pivot = Offset(upperSize / 2f, upperSize / 2f)) {
                            drawOutline(upperOutline, color, alpha = 0.05f)
                        }
                    }
                    translate(left = -84.dp.toPx(), top = size.height - 108.dp.toPx()) {
                        rotate(-18f, pivot = Offset(lowerSize / 2f, lowerSize / 2f)) {
                            drawOutline(lowerOutline, color, alpha = 0.04f)
                        }
                    }
                }
            }
        }
    }
}
