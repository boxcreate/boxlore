package cx.aswin.boxlore.feature.info.branded.serial

import android.animation.ValueAnimator
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.components.OptimizedImage
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.R
import cx.aswin.boxlore.feature.info.branded.BrandedCopyrightButton
import cx.aswin.boxlore.feature.info.branded.BrandedDescription
import cx.aswin.boxlore.feature.info.branded.BrandedDescriptionExtras
import cx.aswin.boxlore.feature.info.branded.awaitPageSettled
import cx.aswin.boxlore.feature.info.branded.boxShadow
import cx.aswin.boxlore.feature.info.branded.brandedSansStyle
import cx.aswin.boxlore.feature.info.branded.rememberBrandedArt

/**
 * The stack: Serial's logo made real. The logo is a stack of black cards with white rims; here each
 * card is a season, the newest on top and six before it stepping down-left behind it, each with its
 * cover and name. When the page has settled the deck deals itself, oldest card first, each season
 * dropping onto the one before with a small bump; then Serial's own card, the S from the logo,
 * slides half out from behind the newest season. A tap on the deck deals it again. Below: SERIAL,
 * the publishers' marks with the ©, the description, centred, which opens on tap, and the chips.
 */
@Composable
internal fun SerialHeader(podcast: Podcast, feed: List<Episode>, description: BrandedDescription, chips: @Composable () -> Unit, modifier: Modifier) {
    val deck = remember(feed, podcast) { serialDeck(feed, podcast) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(SerialArt.HEIGHT.dp)) {
            Backdrop()
            Deck(deck, Modifier.align(Alignment.TopCenter))
            CreditRow(Modifier.align(Alignment.BottomCenter))
        }
        if (description.text.isNotEmpty()) {
            Text(
                description.text,
                style = brandedSansStyle(14.5.sp, 21.sp),
                color = SerialColors.Muted,
                textAlign = TextAlign.Center,
                maxLines = if (description.expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 28.dp, end = 28.dp, top = 14.dp)
                    .fillMaxWidth()
                    .animateContentSize(spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
                    .expressiveClickable(shape = RoundedCornerShape(8.dp)) { description.onToggle() },
            )
        }
        Column(Modifier.padding(horizontal = 16.dp)) { BrandedDescriptionExtras(description) }
        Spacer(Modifier.height(if (description.text.isNotEmpty()) 18.dp else 14.dp))
        chips()
    }
}

/** The cover art's plum, up behind the status bar, a red glow behind the deck, and a shade under the top bar. */
@Composable
private fun Backdrop() {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Spacer(
        Modifier.fillMaxSize().drawBehind {
            val unit = 1.dp.toPx()
            val top = -maxOf(statusBar.toPx(), SerialArt.STATUS_BAR * unit)
            clipRect(top = top, bottom = size.height) {
                drawRect(SerialColors.Plum, topLeft = Offset(0f, top), size = Size(size.width, size.height - top))
                val centre = Offset(size.width / 2f, SerialArt.GLOW_Y * unit)
                val rx = SerialArt.GLOW_RX * unit
                scale(scaleX = 1f, scaleY = SerialArt.GLOW_RY / SerialArt.GLOW_RX, pivot = centre) {
                    drawCircle(Brush.radialGradient(0f to Glow, 0.7f to Glow.copy(alpha = 0f), center = centre, radius = rx), radius = rx, center = centre)
                }
                drawRect(
                    Brush.verticalGradient(0f to Shade, 1f to Shade.copy(alpha = 0f), startY = top, endY = SerialArt.SHADE_BOTTOM * unit),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, SerialArt.SHADE_BOTTOM * unit - top),
                )
            }
        },
    )
}

/** The seasons' cards and Serial's own, in the design's 369dp-wide frame, centred; SERIAL under them. */
@Composable
private fun Deck(deck: List<SerialSeason>, modifier: Modifier) {
    val covers = remember(deck) { deck.mapNotNull { it.cover } }
    val art by rememberBrandedArt(*covers.toIntArray())
    val deal = rememberDeal(ready = art != null)
    val pictures = art?.let { bitmaps -> covers.zip(bitmaps).toMap() }
    val unnamed = deck.associate { it.number to stringResource(R.string.serial_season, it.number) }
    Box(modifier.requiredWidth(SerialArt.WIDTH.dp).height(SerialArt.HEIGHT.dp)) {
        // Drawn from the back: the deepest card first, Serial's own card just behind the newest season.
        for (depth in deck.indices.reversed()) {
            if (depth == 0) PeekCard(deal)
            val season = deck[depth]
            SeasonCard(
                season = season,
                bitmap = season.cover?.let { pictures?.get(it) },
                label = season.label(unnamed.getValue(season.number)),
                modifier = Modifier
                    .offset((SerialArt.DECK_X - SerialArt.STEP * depth).dp, (SerialArt.DECK_Y + SerialArt.STEP * depth).dp)
                    .graphicsLayer {
                        val pose = SerialDeal.card(depth, deck.size, deal.millis())
                        translationX = pose.x.dp.toPx()
                        translationY = pose.y.dp.toPx()
                        rotationZ = pose.rotation
                        scaleX = pose.scale
                        scaleY = pose.scale
                        alpha = pose.alpha
                    },
            )
        }
        Image(
            painterResource(R.drawable.serial_wordmark),
            stringResource(R.string.serial_wordmark_description),
            Modifier
                .offset(SerialArt.WORDMARK_X.dp, SerialArt.WORDMARK_Y.dp)
                .size(SerialArt.WORDMARK_WIDTH.dp, SerialArt.WORDMARK_HEIGHT.dp)
                .semantics { heading() },
            contentScale = ContentScale.FillBounds,
        )
        // The deck deals again on a tap: a flourish for the eye only, so screen readers skip it.
        Spacer(
            Modifier
                .offset(SerialArt.TAP_X.dp, SerialArt.TAP_Y.dp)
                .size(SerialArt.TAP_SIZE.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = deal.again)
                .clearAndSetSemantics {},
        )
    }
}

/** A season's card: black with the logo's white rim, its cover, and its number and name under it. */
@Composable
private fun SeasonCard(season: SerialSeason, bitmap: ImageBitmap?, label: String, modifier: Modifier) {
    val shape = RoundedCornerShape(SerialArt.CARD_CORNER.dp)
    Box(
        modifier
            .size(SerialArt.CARD_WIDTH.dp, SerialArt.CARD_HEIGHT.dp)
            .boxShadow(fill = SerialColors.CardBlack, color = CardShadow, offsetY = 6.dp, blur = 14.dp, cornerRadius = SerialArt.CARD_CORNER.dp)
            .border(SerialArt.RIM.dp, SerialColors.Rim, shape)
            .clearAndSetSemantics {},
    ) {
        val picture = Modifier
            .padding(SerialArt.COVER_INSET.dp)
            .size(SerialArt.COVER.dp)
            .clip(RoundedCornerShape(5.dp))
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = picture, contentScale = ContentScale.Crop)
        } else if (season.cover == null) {
            OptimizedImage(url = season.picture, proxyWidth = 320, contentDescription = null, modifier = picture)
        }
        Text(
            label,
            style = brandedSansStyle(8.5.sp, 11.sp, weight = 700).copy(letterSpacing = 1.3.sp),
            color = SerialColors.Text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 8.dp, bottom = 9.dp)
                .fillMaxWidth(),
        )
    }
}

/** Serial's own card, pulled half out of the deck behind the newest season once the seasons are down. */
@Composable
private fun PeekCard(deal: SerialDealClock) {
    Spacer(
        Modifier
            .offset(SerialArt.PEEK_X.dp, SerialArt.PEEK_Y.dp)
            .size(SerialArt.CARD_WIDTH.dp, SerialArt.CARD_HEIGHT.dp)
            .graphicsLayer {
                val pose = SerialDeal.peek(deal.millis())
                translationX = pose.x.dp.toPx()
                rotationZ = pose.rotation
                alpha = pose.alpha
                transformOrigin = TransformOrigin(0.2f, 0.8f)
            }
            .boxShadow(fill = SerialColors.CardBlack, color = CardShadow, offsetY = 6.dp, blur = 10.dp, cornerRadius = SerialArt.CARD_CORNER.dp)
            .drawBehind { drawSerialCard(size, SerialArt.CARD_CORNER.dp.toPx(), SerialArt.RIM.dp.toPx()) },
    )
}

/** The publishers' own marks, as the season covers sign them: the Times' T and Serial Productions' S, then the ©. */
@Composable
private fun CreditRow(modifier: Modifier) {
    Row(modifier.height(SerialArt.CREDITS_HEIGHT.dp), verticalAlignment = Alignment.CenterVertically) {
        // The © button keeps its own space beside the mark; the same on this side keeps the marks centred.
        Spacer(Modifier.width(COPYRIGHT_SIDE))
        Image(
            painterResource(R.drawable.serial_nyt_t),
            stringResource(R.string.serial_nyt_description),
            Modifier.size(11.5.dp, 15.dp).alpha(MARK_ALPHA),
            contentScale = ContentScale.FillBounds,
        )
        CreditRule(MARK_GAP, MARK_GAP)
        Image(
            painterResource(R.drawable.serial_productions_s),
            stringResource(R.string.serial_productions_description),
            Modifier.size(14.dp).alpha(MARK_ALPHA),
            contentScale = ContentScale.FillBounds,
        )
        CreditRule(MARK_GAP, MARK_GAP - COPYRIGHT_SIDE)
        BrandedCopyrightButton(SerialColors.Copyright, stringResource(R.string.serial_copyright))
    }
}

@Composable
private fun CreditRule(before: Dp, after: Dp) {
    Spacer(Modifier.width(before))
    Spacer(Modifier.size(1.dp, 12.dp).background(SerialColors.Muted.copy(alpha = 0.45f)))
    Spacer(Modifier.width(after))
}

/**
 * The deal's clock in milliseconds, read while drawing. It starts once the covers are decoded and the
 * page has settled, the first time the page opens; it stands at the end after that, and always with
 * animations off.
 */
@Stable
private class SerialDealClock(private val clock: Animatable<Float, *>, val again: () -> Unit) {
    fun millis(): Float = clock.value
}

@Composable
private fun rememberDeal(ready: Boolean): SerialDealClock {
    val still = remember { !ValueAnimator.areAnimatorsEnabled() }
    var dealt by rememberSaveable { mutableStateOf(false) }
    val clock = remember { Animatable(if (dealt) SerialDeal.END else 0f) }
    var replays by remember { mutableIntStateOf(0) }
    LaunchedEffect(ready, still, replays) {
        if (!ready) return@LaunchedEffect
        if (still) {
            clock.snapTo(SerialDeal.END)
            dealt = true
            return@LaunchedEffect
        }
        if (replays == 0) {
            if (dealt) return@LaunchedEffect
            awaitPageSettled()
        }
        dealt = true
        clock.snapTo(0f)
        clock.animateTo(SerialDeal.END, tween(SerialDeal.END.toInt(), easing = LinearEasing))
    }
    return remember(clock, still) { SerialDealClock(clock) { if (!still) replays++ } }
}

/** The header's frame in the design's units (dp), from the top of the status bar, which is 43dp tall there. */
internal object SerialArt {
    const val WIDTH = 369f
    const val STATUS_BAR = 43f
    const val HEIGHT = 330f - STATUS_BAR
    const val CARD_WIDTH = 114f
    const val CARD_HEIGHT = 140f
    const val CARD_CORNER = 13f
    const val RIM = 2.5f
    const val COVER_INSET = 6f
    const val COVER = CARD_WIDTH - 2 * COVER_INSET
    const val STEP = 6f
    const val DECK_X = 133f
    const val DECK_Y = 100f - STATUS_BAR
    const val PEEK_X = DECK_X + 50f
    const val PEEK_Y = DECK_Y - 4f
    const val WORDMARK_X = 124f
    const val WORDMARK_Y = 283f - STATUS_BAR
    const val WORDMARK_WIDTH = 121f
    const val WORDMARK_HEIGHT = 17.3f
    const val CREDITS_HEIGHT = 24f
    const val GLOW_Y = 195f - STATUS_BAR
    const val GLOW_RX = 150f
    const val GLOW_RY = 125f
    const val SHADE_BOTTOM = 96f - STATUS_BAR
    const val TAP_X = 80f
    const val TAP_Y = 90f - STATUS_BAR
    const val TAP_SIZE = 190f
}

private val Glow = Color(0x38EB1C24)
private val Shade = Color(0x8C170911)
private val CardShadow = Color(0x73080205)
private val MARK_GAP = 9.dp
private val COPYRIGHT_SIDE = 8.dp
private const val MARK_ALPHA = 0.9f
