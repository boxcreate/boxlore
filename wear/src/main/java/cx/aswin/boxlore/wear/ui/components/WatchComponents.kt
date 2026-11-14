package cx.aswin.boxlore.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage

/** Show artwork, with the tonal container showing while it loads or when there is none. */
@Composable
internal fun Artwork(
    url: String?,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(9.dp),
) {
    Box(modifier = modifier.clip(shape).background(MaterialTheme.colorScheme.primaryContainer)) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** A tall pill row: artwork or an icon, a title over up to two lines, and one line underneath. */
@Composable
internal fun WatchRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondary: (@Composable () -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    transformation: SurfaceTransformation? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        transformation = transformation,
    ) {
        if (leading != null) {
            leading()
            Box(Modifier.size(9.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = title,
                style = if (ScreenFit.isCompact(rememberScreenScale())) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            secondary?.invoke()
        }
        if (trailing != null) {
            Box(Modifier.size(6.dp))
            trailing()
        }
    }
}

/** The quiet line under a row title. [emphasised] uses the "new" accent. */
@Composable
internal fun RowDetail(text: String, emphasised: Boolean = false, icon: ImageVector? = null, iconLabel: String? = null) {
    val color = if (emphasised) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = iconLabel,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(13.dp),
            )
            Box(Modifier.size(3.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A round tonal disc holding an icon, used where a row has no artwork. */
@Composable
internal fun IconDisc(icon: ImageVector, modifier: Modifier = Modifier, size: Dp = 34.dp) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(size * 0.53f))
    }
}

/** What a list shows when it has nothing: what is missing and, where it helps, what to do next. */
@Composable
internal fun EmptyMessage(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconDisc(icon = icon, size = 36.dp)
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A name followed by something short that must stay whole, such as "Cyber Hack · 22 min left".
 * When the row is too narrow the name is cut with an ellipsis and [tail] stays.
 */
@Composable
internal fun NameWithTail(name: String, tail: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (name.isNotBlank()) {
            Text(text = name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Text(text = " · ", style = style, color = color, maxLines = 1)
        }
        Text(text = tail, style = style, color = color, maxLines = 1)
    }
}
