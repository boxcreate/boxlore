package cx.aswin.boxlore.feature.info.branded

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Copyright
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import cx.aswin.boxlore.feature.info.R

/** A branded page's © button and the popup it opens, in the show's colours. */
@Immutable
internal data class BrandedCopyrightStyle(
    val icon: Color,
    val popup: Color,
    val title: Color,
    val body: Color,
    val note: Color,
    val shadow: Color,
    val ring: Color,
    val popupWidth: Dp,
    /** Where the caret's centre sits, from the card's left edge; it points at the ©. */
    val caretCentre: Dp,
    /** Space between the © button's bottom and the card. */
    val gap: Dp,
    /** How far the 30dp button reaches past its row, above and below, without making the row taller. */
    val overhangTop: Dp,
    val overhangBottom: Dp,
    val iconSize: Dp = 17.dp,
)

/**
 * A 30dp © with a full 48dp touch target that takes only `30 - overhang` of its row's height.
 * Tapping it opens [copyright] in a small popup under it.
 */
@Composable
internal fun BrandedCopyrightButton(style: BrandedCopyrightStyle, copyright: String, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    val label = stringResource(R.string.branded_copyright_button)
    Box(
        modifier
            .layout { measurable, constraints ->
                // Lay out smaller than drawn (the design's negative margins); touch and drawing keep their full size.
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                val width = 30.dp.roundToPx()
                val top = style.overhangTop.roundToPx()
                val height = 30.dp.roundToPx() - top - style.overhangBottom.roundToPx()
                layout(width, height) { placeable.place((width - placeable.width) / 2, (30.dp.roundToPx() - placeable.height) / 2 - top) }
            }
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 15.dp),
                role = Role.Button,
                onClick = { open = !open },
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Copyright, null, Modifier.size(style.iconSize), tint = style.icon)
            if (open) CopyrightPopup(style, copyright, onDismiss = { open = false })
        }
    }
}

@Composable
private fun CopyrightPopup(style: BrandedCopyrightStyle, copyright: String, onDismiss: () -> Unit) {
    val density = LocalDensity.current
    val position = remember(density, style) { CopyrightPopupPosition(density, style) }
    // Unclipped, so the shadow may run past the screen edge without Android moving the card.
    Popup(popupPositionProvider = position, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        // Room around the card for its caret above and its shadow on every side.
        Box(Modifier.padding(start = PopupShadowRoom, end = PopupShadowRoom, top = PopupCaretRoom, bottom = PopupShadowRoom + 10.dp)) {
            Box(
                Modifier
                    .offset(x = style.caretCentre - 6.dp, y = (-5).dp)
                    .size(12.dp)
                    .rotate(45f)
                    .background(style.popup, RoundedCornerShape(2.dp))
                    .border(1.dp, style.ring, RoundedCornerShape(2.dp)),
            )
            Column(
                Modifier
                    .width(style.popupWidth)
                    .boxShadow(fill = style.popup, color = style.shadow, offsetY = 10.dp, blur = 28.dp, cornerRadius = 14.dp)
                    .border(1.dp, style.ring, RoundedCornerShape(14.dp))
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 14.dp)
                    .semantics(mergeDescendants = true) {},
            ) {
                Text(stringResource(R.string.branded_copyright_title), style = brandedSansStyle(14.sp, 18.sp, weight = 500), color = style.title)
                Text(copyright, style = brandedSansStyle(13.sp, 18.sp), color = style.body, modifier = Modifier.padding(top = 4.dp))
                Text(
                    stringResource(R.string.branded_copyright_note),
                    style = brandedSansStyle(12.sp, 16.sp),
                    color = style.note,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private val PopupShadowRoom = 24.dp
private val PopupCaretRoom = 8.dp

/**
 * Puts the caret's centre under the ©, [BrandedCopyrightStyle.gap] below it. In windows too narrow
 * for that, the card keeps an 8dp margin from the right edge instead.
 */
private class CopyrightPopupPosition(private val density: Density, private val style: BrandedCopyrightStyle) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset =
        with(density) {
            val cardLeft = (anchorBounds.center.x - style.caretCentre.roundToPx())
                .coerceAtMost(windowSize.width - (style.popupWidth + 8.dp).roundToPx())
                .coerceAtLeast(8.dp.roundToPx())
            IntOffset(
                x = cardLeft - PopupShadowRoom.roundToPx(),
                y = anchorBounds.bottom + style.gap.roundToPx() - PopupCaretRoom.roundToPx(),
            )
        }
}
