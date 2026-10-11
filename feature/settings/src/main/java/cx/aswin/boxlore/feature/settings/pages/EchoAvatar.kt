package cx.aswin.boxlore.feature.settings.pages

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cx.aswin.boxlore.feature.settings.R
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private data class EchoPack(val atlas: ImageBitmap, val spin: ImageBitmap, val loops: Map<String, EchoLoop>, val spinLoop: EchoLoop)
private val EchoOutfits = listOf("idle", "comedy", "true-crime", "science-tech", "history", "business", "wellness", "fiction")

/** Bundled pixels, decoded once per page. Animation never controls actual sync status. */
@Composable
internal fun EchoAvatar(syncing: Boolean, modifier: Modifier = Modifier) {
    val assets = LocalContext.current.applicationContext.assets
    val pack by produceState<EchoPack?>(null, assets) { value = loadEchoPack(assets) }
    var outfit by rememberSaveable { mutableStateOf("idle") }
    val interactionSource = remember { MutableInteractionSource() }
    val target = rememberUpdatedState(if (syncing) "syncing" else outfit)
    var visible by remember { mutableStateOf(false) }
    val frame = rememberEchoFrame(pack, visible, target)
    val description = stringResource(R.string.account_echo_description)
    val avatarModifier = modifier.testTag(if (pack == null) "account_echo_loading" else "account_echo_avatar").onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        visible = bounds.height > 0 && bounds.width > 0
    }.then(
        if (syncing) {
        Modifier
    } else {
        Modifier.semantics { contentDescription = description }
        .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClickLabel = stringResource(R.string.account_echo_change)) {
            outfit = EchoOutfits[(EchoOutfits.indexOf(outfit) + 1) % EchoOutfits.size]
        }
    }
    )
    val shownPack = pack
    if (shownPack == null) {
        Box(avatarModifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.CloudSync, null, Modifier.size(36.dp))
        }
    } else {
        Canvas(avatarModifier) { drawEchoFrame(shownPack, frame.value) }
    }
}

private suspend fun loadEchoPack(assets: AssetManager): EchoPack? = try {
    withContext(Dispatchers.IO) {
        val loops = assets.open("echo/animation-manifest.json").bufferedReader().use { parseEchoLoops(it.readText()) }
        val spinSpec = assets.open("echo/transitions/shared-spin/transition.json").bufferedReader().use {
            Json.parseToJsonElement(it.readText()).jsonObject
        }
        val atlas = assets.open("echo/echo-atlas.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
        val spin = assets.open("echo/transitions/shared-spin/strip.png").use { requireNotNull(BitmapFactory.decodeStream(it)) }
        require(atlas.width == 1536 && atlas.height == 1872 && spin.width == 1536 && spin.height == 208)
        EchoPack(atlas.asImageBitmap(), spin.asImageBitmap(), loops, EchoLoop(0, spinSpec.getValue("frame_durations_ms").jsonArray.map { it.jsonPrimitive.int }))
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    Log.w("EchoAvatar", "Unable to load bundled avatar", failure)
    null
}

@Composable
private fun rememberEchoFrame(pack: EchoPack?, visible: Boolean, target: State<String>): State<EchoFrame> {
    val owner = LocalLifecycleOwner.current
    val frame = remember { mutableStateOf(EchoFrame(EchoPose(target.value, 0))) }
    LaunchedEffect(pack, owner, visible) {
        if (pack == null || !visible) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val motion = currentCoroutineContext()[MotionDurationScale]
            snapshotFlow { motion?.scaleFactor ?: 1f }.collectLatest { scale ->
                if (scale <= 0f) {
                    snapshotFlow { target.value }.collect { frame.value = EchoFrame(EchoPose(it, 0)) }
                } else {
                    animateEchoFrames(pack, target, scale) { frame.value = it }
                }
            }
        }
    }
    return frame
}

private suspend fun animateEchoFrames(pack: EchoPack, target: State<String>, scale: Float, onFrame: (EchoFrame) -> Unit) {
    val playback = EchoPlayback(pack.loops, pack.spinLoop, target.value)
    val start = withInfiniteAnimationFrameNanos { it }
    while (currentCoroutineContext().isActive) {
        val now = withInfiniteAnimationFrameNanos { it }
        onFrame(playback.frame(((now - start) / (1_000_000 * scale)).toLong(), target.value))
    }
}

private fun DrawScope.drawEchoFrame(pack: EchoPack, frame: EchoFrame) {
    val scale = min(size.width / 192, size.height / 208)
    val destination = IntSize((192 * scale).roundToInt(), (208 * scale).roundToInt())
    if (destination.width <= 0 || destination.height <= 0) return
    listOfNotNull(frame.first, frame.second).forEach { pose ->
        val row = if (pose.state == "spin") 0 else pack.loops.getValue(pose.state).row
        drawImage(
            image = if (pose.state == "spin") pack.spin else pack.atlas,
            srcOffset = IntOffset(pose.frame * 192, row * 208),
            srcSize = IntSize(192, 208),
            dstOffset = IntOffset(((size.width - destination.width) / 2).roundToInt(), ((size.height - destination.height) / 2).roundToInt()),
            dstSize = destination,
            alpha = pose.alpha,
            filterQuality = FilterQuality.Medium,
        )
    }
}
