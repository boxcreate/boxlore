package cx.aswin.boxlore.feature.info.branded.rottenmango

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedButtonNudge
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeLabels
import cx.aswin.boxlore.feature.info.branded.BrandedSubscribeMoment
import cx.aswin.boxlore.feature.info.branded.BrandedToolbarStyle
import cx.aswin.boxlore.feature.info.branded.MomentHaptic
import cx.aswin.boxlore.feature.info.branded.brandedTextStyle
import cx.aswin.boxlore.feature.info.components.SubscriptionLabel
import kotlin.math.min

private val SplitRed = Color(0xD9FF2E46)
private val SplitCyan = Color(0xCC3CDCFF)
private val WordGlow = Color(0x80FFECD6)

/**
 * "Bada bing, bada boom" on tape, the way the show plays its catchphrase: the Subscribe button
 * switches on like an old television, "BADA BING" plays grainy and jittery, a burst of static cuts
 * in, "BADA BOOM" hits with its colours split, a jump in the picture and one buzz, then the set
 * switches off to a line and the button settles on Subscribed. About 1.5 seconds, no sound.
 */
internal object RottenMangoOnTape : BrandedSubscribeMoment {
    override val durationMillis = RottenMangoTape.DURATION_MILLIS
    override val revealDelayMillis = RottenMangoTape.REVEAL_MILLIS

    override fun nudge(elapsedMillis: Float) = BrandedButtonNudge(0.dp, 1f)

    @Composable
    override fun Overlay(elapsedMillis: () -> Float, labels: BrandedSubscribeLabels, style: BrandedToolbarStyle, shape: Shape, modifier: Modifier) {
        MomentHaptic(elapsedMillis, RottenMangoTape.BOOM_MILLIS, HapticFeedbackType.LongPress)
        val noise = rememberTapeNoise()
        val words = rememberTapeWords()
        Box(modifier.clip(shape), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val millis = elapsedMillis()
                drawRect(if (RottenMangoTape.baseBlack(millis)) RottenMangoColors.TapeBlack else style.subscribeContainer)
                RottenMangoTape.screen(millis)?.let { drawScreen(it, millis, noise, words) }
                drawRect(style.subscribedContainer, alpha = RottenMangoTape.settle(millis))
            }
            SubscriptionLabel(
                text = labels.subscribe,
                compact = labels.compact,
                color = style.subscribeContent,
                modifier = Modifier.graphicsLayer { alpha = RottenMangoTape.subscribeLabel(elapsedMillis()) },
            )
            SubscriptionLabel(
                text = labels.subscribed,
                compact = labels.compact,
                color = style.subscribedContent,
                modifier = Modifier.graphicsLayer {
                    val (landAlpha, landScale) = RottenMangoTape.subscribedLabel(elapsedMillis())
                    alpha = landAlpha
                    scaleX = landScale
                    scaleY = landScale
                },
            )
        }
    }
}

/** The two catchphrase words, laid out once. */
private class TapeWords(val bing: TextLayoutResult, val boom: TextLayoutResult)

/** VT323 sized in dp: the words are part of the picture, which never grows with the font size setting. */
@Composable
private fun rememberTapeWords(): TapeWords {
    val measurer = rememberTextMeasurer()
    val tape = rememberRottenMangoTape()
    val density = LocalDensity.current
    val bing = stringResource(R.string.rottenmango_bada_bing)
    val boom = stringResource(R.string.rottenmango_bada_boom)
    return remember(measurer, tape, density, bing, boom) {
        with(density) {
            TapeWords(
                measurer.measure(bing, brandedTextStyle(tape, 31.dp.toSp(), 31.dp.toSp(), letterSpacing = 2.dp.toSp())),
                measurer.measure(boom, brandedTextStyle(tape, 37.dp.toSp(), 37.dp.toSp(), letterSpacing = 2.dp.toSp())),
            )
        }
    }
}

/** The set's picture, squeezed and brightened as it switches on and off. */
private fun DrawScope.drawScreen(screen: RottenMangoTape.Screen, millis: Float, noise: ImageBitmap, words: TapeWords) {
    val unit = 1.dp.toPx()
    scale(screen.scaleX, screen.scaleY, pivot = center) {
        val layer = Paint().apply {
            alpha = screen.alpha
            if (screen.brightness != 1f) colorFilter = brightness(screen.brightness)
        }
        drawIntoCanvas { it.saveLayer(size.toRect(), layer) }
        drawCssEllipse(this, 0.8f, 1.3f, arrayOf(0f to RottenMangoColors.TapeGlow, 0.68f to RottenMangoColors.TapeDim, 1f to RottenMangoColors.TapeBlack))
        translate(top = RottenMangoTape.hold(millis) * unit) {
            RottenMangoTape.bing(millis)?.let { drawWord(words.bing, it, scale = 1f, split = 2f, glow = 7f) }
            RottenMangoTape.boom(millis)?.let { drawWord(words.boom, it.jitter, it.scale, it.split, it.glow) }
            drawGrain(noise, RottenMangoTape.grainAlpha(millis), RottenMangoTape.grain(millis), RottenMangoTape.grainGlow(millis))
        }
        RottenMangoTape.trackingLines(millis).forEach { rolled ->
            val top = lerp(size.height, -12 * unit, rolled)
            drawGrain(noise, alpha = 0.75f, glow = 1.6f, area = Rect(Offset(0f, top), Size(size.width, 9 * unit)))
        }
        // Scanlines: a dark line every 3dp.
        var line = 0f
        while (line < size.height) {
            drawRect(Color(0x73000000), topLeft = Offset(0f, line), size = Size(size.width, unit))
            line += 3 * unit
        }
        drawCssEllipse(this, 0.72f, 1.5f, arrayOf(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color(0x9E000000)))
        drawIntoCanvas { it.restore() }
    }
}

/** A radial gradient as CSS draws "ellipse W% H% at 50% 50%": [width] and [height] are fractions of the button. */
private fun drawCssEllipse(scope: DrawScope, width: Float, height: Float, stops: Array<Pair<Float, Color>>) = with(scope) {
    val radiusX = size.width * width
    scale(scaleX = 1f, scaleY = size.height * height / radiusX, pivot = center) {
        drawRect(
            Brush.radialGradient(*stops, center = center, radius = radiusX),
            topLeft = Offset(0f, center.y - radiusX),
            size = Size(size.width, radiusX * 2),
        )
    }
}

/** A word with its colours split [split] dp either side, over a soft glow, shrunk to fit a narrow button. */
private fun DrawScope.drawWord(word: TextLayoutResult, jitter: Offset, scale: Float, split: Float, glow: Float) {
    val unit = 1.dp.toPx()
    val fit = min(1f, (size.width - 24 * unit) / word.size.width)
    val topLeft = Offset((size.width - word.size.width) / 2f, (size.height - word.size.height) / 2f) + jitter * unit
    scale(scale * fit, pivot = center) {
        drawText(word, RottenMangoColors.TapeText, topLeft, shadow = Shadow(WordGlow, Offset.Zero, glow * unit * 0.75f))
        drawText(word, SplitRed, topLeft + Offset(-split * unit, 0f))
        drawText(word, SplitCyan, topLeft + Offset(split * unit, 0f))
        drawText(word, RottenMangoColors.TapeText, topLeft)
    }
}
