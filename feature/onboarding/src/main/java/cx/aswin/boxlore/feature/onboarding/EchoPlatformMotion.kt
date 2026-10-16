package cx.aswin.boxlore.feature.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.floor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest

/** One soft silhouette for each possible question, revisited consistently when going back. */
internal enum class EchoPlatformSilhouette { PUFFY, COOKIE_FOUR, COOKIE_SIX, COOKIE_NINE, PUFFY_DIAMOND, SUNNY, CLOVER }

internal fun echoPlatformSilhouette(questionKey: String): EchoPlatformSilhouette {
    val turn = questionKey.substringBefore(':').toIntOrNull() ?: 1
    return EchoPlatformSilhouette.entries[(turn.toLong() - 1L).coerceIn(0L, EchoPlatformSilhouette.entries.lastIndex.toLong()).toInt()]
}

internal data class EchoPlatformPose(val from: Int, val to: Int, val progress: Float, val rotation: Float)

/** Each new question turns the stage a further 45° while it changes shape. */
private val EchoPlatformAngles = List(EchoPlatformSilhouette.entries.size) { index -> -8f + index * 45f }

/** Adjacent polygon morphs share endpoints, including interrupted or reversed transitions. */
internal fun echoPlatformPose(position: Float): EchoPlatformPose {
    val bounded = if (position.isFinite()) position.coerceIn(0f, EchoPlatformSilhouette.entries.lastIndex.toFloat()) else 0f
    val from = floor(bounded).toInt()
    val to = (from + 1).coerceAtMost(EchoPlatformSilhouette.entries.lastIndex)
    val progress = bounded - from
    val rotation = EchoPlatformAngles[from] + (EchoPlatformAngles[to] - EchoPlatformAngles[from]) * progress
    return EchoPlatformPose(from, to, progress, rotation)
}

/** Each accepted question morphs and turns Echo's stage on a spring; Echo itself stays upright. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EchoQuestionPlatform(questionKey: String, modifier: Modifier = Modifier) {
    val target = echoPlatformSilhouette(questionKey).ordinal.toFloat()
    val position = rememberEchoPlatformPosition(target)
    val morphs = remember {
        val polygons = EchoPlatformSilhouette.entries.map { it.polygon().normalized() }
        polygons.mapIndexed { index, polygon -> Morph(polygon, polygons[(index + 1).coerceAtMost(polygons.lastIndex)]) }
    }
    val path = remember { Path() }
    val matrix = remember { Matrix() }
    val color = MaterialTheme.colorScheme.primaryContainer
    Canvas(modifier.testTag("echo_avatar_platform")) {
        val pose = echoPlatformPose(position.value)
        morphs[pose.from].toPath(pose.progress, path)
        // At this fill every shape stays inside the canvas at any angle (the widest reaches 1.1x its size).
        matrix.reset()
        matrix.scale(size.width * EchoPlatformFill, size.height * EchoPlatformFill)
        path.transform(matrix)
        path.translate(Offset(size.width / 2f, size.height / 2f) - path.getBounds().center)
        rotate(pose.rotation) { drawPath(path, color) }
    }
}

/** The share of its canvas a stage shape fills, before turning. */
internal const val EchoPlatformFill = 0.9f

/** Slightly underdamped, so the new shape settles with a small, expressive wobble. */
private val EchoPlatformSpring = spring<Float>(dampingRatio = 0.62f, stiffness = 130f)

@Composable
private fun rememberEchoPlatformPosition(target: Float): State<Float> {
    val position = remember { Animatable(target) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(target, lifecycle) {
        val scale = currentCoroutineContext()[MotionDurationScale]
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            snapshotFlow { scale?.scaleFactor ?: 1f }.collectLatest { factor ->
                if (factor <= 0f) {
                    position.snapTo(target)
                } else {
                    position.animateTo(target, EchoPlatformSpring)
                }
            }
        }
    }
    return position.asState()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun EchoPlatformSilhouette.polygon(): RoundedPolygon = when (this) {
    EchoPlatformSilhouette.PUFFY -> MaterialShapes.Puffy
    EchoPlatformSilhouette.COOKIE_FOUR -> MaterialShapes.Cookie4Sided
    EchoPlatformSilhouette.COOKIE_SIX -> MaterialShapes.Cookie6Sided
    EchoPlatformSilhouette.COOKIE_NINE -> MaterialShapes.Cookie9Sided
    EchoPlatformSilhouette.PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond
    EchoPlatformSilhouette.SUNNY -> MaterialShapes.Sunny
    EchoPlatformSilhouette.CLOVER -> MaterialShapes.Clover4Leaf
}
