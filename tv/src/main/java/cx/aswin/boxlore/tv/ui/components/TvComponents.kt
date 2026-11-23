package cx.aswin.boxlore.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import cx.aswin.boxlore.tv.ui.theme.TvColors
import cx.aswin.boxlore.tv.ui.theme.TvSpace

/** How much a card grows when the remote is on it. Rows leave room for it, so nothing is covered. */
const val CONTENT_FOCUS_SCALE = 1.06f
private const val BUTTON_FOCUS_SCALE = 1.06f
private val FocusOutlineWidth = 3.dp

/** Cover art cropped to [shape], with a quiet fill while it loads or when there is none. */
@Composable
fun Artwork(url: String?, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(16.dp)) {
    Box(modifier.clip(shape).background(TvColors.surfaceHigh)) {
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * A pill button: resting on a quiet fill, iris when the remote is on it. Every action on the TV
 * uses it, so "the iris pill" always means "this is what OK will do".
 */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    restingColor: Color = TvColors.surfaceHighest,
    restingContentColor: Color = TvColors.onSurface,
    trailingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.shape(shape = CircleShape),
        colors =
        ButtonDefaults.colors(
            containerColor = restingColor,
            contentColor = restingContentColor,
            focusedContainerColor = TvColors.primary,
            focusedContentColor = TvColors.onPrimary,
            pressedContainerColor = TvColors.primary,
            pressedContentColor = TvColors.onPrimary,
        ),
        scale = ButtonDefaults.scale(focusedScale = BUTTON_FOCUS_SCALE),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** A round icon-only button, for actions whose icon is universal (play, skip, like). */
@Composable
fun RoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    restingColor: Color = TvColors.surfaceHighest,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        colors =
        ClickableSurfaceDefaults.colors(
            containerColor = restingColor,
            contentColor = TvColors.onSurface,
            focusedContainerColor = TvColors.primary,
            focusedContentColor = TvColors.onPrimary,
            pressedContainerColor = TvColors.primary,
            pressedContentColor = TvColors.onPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = BUTTON_FOCUS_SCALE + 0.04f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.5f))
        }
    }
}

/**
 * A piece of content the remote can land on (a card, a row, a cover): it grows a little and takes
 * a light outline, so focus reads on any artwork.
 */
@Composable
fun ContentSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    onLongClick: (() -> Unit)? = null,
    restingColor: Color = TvColors.surface,
    focusScale: Float = CONTENT_FOCUS_SCALE,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors =
        ClickableSurfaceDefaults.colors(
            containerColor = restingColor,
            contentColor = TvColors.onSurface,
            focusedContainerColor = if (restingColor == Color.Transparent) TvColors.surfaceHighest else TvColors.surfaceHigh,
            focusedContentColor = TvColors.onSurface,
            pressedContainerColor = TvColors.surfaceHigh,
            pressedContentColor = TvColors.onSurface,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusScale),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(FocusOutlineWidth, TvColors.focusOutline), shape = shape)),
        content = content,
    )
}

/** A row heading with an optional trailing count or action. */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = TvColors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A thin progress bar for started episodes. */
@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, track: Color = TvColors.surfaceHighest, fill: Color = TvColors.primary) {
    Box(modifier.height(5.dp).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(5.dp).clip(CircleShape).background(fill))
    }
}

/** Room a sliding row keeps on its left, so the item the remote lifts keeps its whole outline. */
private val RowLiftRoom = 24.dp

/** Room a sliding row keeps above and below its items, for the same reason. */
private val RowLiftRoomVertical = 10.dp

/**
 * A sliding row's padding: items start where the page starts, a lifted item keeps its outline, and
 * the last item can scroll clear of the screen's right edge. Use with [bleedRow].
 */
val RowPadding = PaddingValues(start = RowLiftRoom, end = TvSpace.safeSide, top = RowLiftRoomVertical, bottom = RowLiftRoomVertical)

/**
 * Widens a sliding row past the page's padding: a little on the left for the lifted item, and out
 * to the screen's right edge, where a slice of the next item shows there is more.
 */
fun Modifier.bleedRow(): Modifier =
    layout { measurable, constraints ->
        val start = RowLiftRoom.roundToPx()
        val end = TvSpace.safeSide.roundToPx()
        val wide =
            if (constraints.hasBoundedWidth) {
                constraints.copy(minWidth = constraints.minWidth + start + end, maxWidth = constraints.maxWidth + start + end)
            } else {
                constraints
            }
        val placeable = measurable.measure(wide)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        layout(width, placeable.height) { placeable.place(-start, 0) }
    }
