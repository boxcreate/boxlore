package cx.aswin.boxlore.core.designsystem.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import kotlinx.coroutines.delay

/**
 * Set while a watch with boxlore is in reach of this phone: what is playing can move to it.
 * Null everywhere else, which hides every control built on it.
 */
@Immutable
class WatchPlayOffer(val onPlayOnWatch: () -> Unit)

/** Provided by the app's root; the players read it to show [PlayingOnPill]. */
val LocalWatchPlayOffer = staticCompositionLocalOf<WatchPlayOffer?> { null }

/**
 * Says where the episode is playing and lets the listener move it: "Playing on" with a phone and a
 * small arrow; a tap opens a short menu with this phone ticked and Play on watch. [quiet] is the
 * minimal player's version: the phone and the arrow alone, with no pill and no words.
 */
@Composable
fun PlayingOnPill(offer: WatchPlayOffer, colorScheme: ColorScheme, modifier: Modifier = Modifier, quiet: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    // The menu hangs from the pill's start by default; knowing the pill's width lets it sit centred under it.
    var pillWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val description = stringResource(R.string.playing_on_description)
    Box(modifier.onSizeChanged { pillWidth = with(density) { it.width.toDp() } }) {
        val tint = if (quiet) colorScheme.onSurface.copy(alpha = 0.6f) else colorScheme.onSurface
        Row(
            modifier =
            Modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 40.dp)
                .clip(CircleShape)
                .background(if (quiet) androidx.compose.ui.graphics.Color.Transparent else colorScheme.onSurface.copy(alpha = 0.1f))
                .clickable(role = Role.Button) { open = true }
                .clearAndSetSemantics {
                    contentDescription = description
                    role = Role.Button
                }
                .padding(start = if (quiet) 12.dp else 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            if (!quiet) {
                Text(
                    text = stringResource(R.string.playing_on_label),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = tint, modifier = Modifier.size(if (quiet) 18.dp else 20.dp))
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = tint.copy(alpha = 0.8f), modifier = Modifier.size(if (quiet) 16.dp else 20.dp))
        }
        // The players carry their own colours; the menu takes them too.
        MaterialTheme(colorScheme = colorScheme) {
            // A soft, rounded sheet with pill rows, like the app's other choosers; the stock menu is square.
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                offset = DpOffset(PlaceMenu.startOffset(pillWidth), 8.dp),
                shape = RoundedCornerShape(28.dp),
                containerColor = colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.width(PlaceMenu.Width).padding(horizontal = 8.dp),
            ) {
                PlaceRow(
                    label = stringResource(R.string.playing_on_this_phone),
                    icon = Icons.Rounded.Smartphone,
                    selected = true,
                    colorScheme = colorScheme,
                    onClick = { open = false },
                )
                Spacer(Modifier.height(4.dp))
                PlaceRow(
                    label = stringResource(R.string.play_on_watch),
                    icon = Icons.Rounded.Watch,
                    selected = false,
                    colorScheme = colorScheme,
                    onClick = {
                        open = false
                        offer.onPlayOnWatch()
                    },
                )
            }
        }
    }
}

/** The menu's fixed width, and where it starts so that it is centred under a pill of a given width. */
internal object PlaceMenu {
    val Width = 240.dp

    fun startOffset(pillWidth: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp = (pillWidth - Width) / 2
}

/** One device in the menu: a full pill, filled for the one playing now, with a tick. */
@Composable
private fun PlaceRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, colorScheme: ColorScheme, onClick: () -> Unit) {
    val content = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface
    Row(
        modifier =
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(if (selected) colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

/**
 * Shown at the top of the screen right after this phone started playing while the watch was: the
 * watch has been paused, and one tap gives playback back to it. Built like the sleep timer prompt,
 * and hides itself after [autoHideMillis] untouched.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackPlaceNotice(
    visible: Boolean,
    onPlayOnWatch: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    autoHideMillis: Long = 8_000L,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) + slideInVertically(tween(320)) { -it / 3 },
        exit = fadeOut(tween(SleepPromptMotion.EXIT_MILLIS)) + slideOutVertically(tween(SleepPromptMotion.EXIT_MILLIS)) { -it / 5 },
        modifier = modifier,
    ) {
        LaunchedEffect(Unit) {
            delay(autoHideMillis)
            dismiss()
        }
        val colors = MaterialTheme.colorScheme
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 100.dp).clip(RoundedCornerShape(20.dp)).background(colors.primaryContainer)) {
                    Box(Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(64.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(64.dp).background(colors.primary, MaterialShapes.Cookie9Sided.toShape()))
                        Icon(Icons.Rounded.Smartphone, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.align(Alignment.CenterStart).padding(start = 92.dp, end = 48.dp, top = 16.dp, bottom = 16.dp)) {
                        Text(stringResource(R.string.watch_notice_title), style = MaterialTheme.typography.titleMedium, color = colors.onPrimaryContainer)
                        Text(
                            stringResource(R.string.watch_notice_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onPrimaryContainer,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    IconButton(onClick = dismiss, modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp).size(40.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.sleep_prompt_dismiss), tint = colors.onPrimaryContainer, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                PlayOnWatchInsteadButton(onClick = onPlayOnWatch)
            }
        }
    }
}

/** The tonal "Play on watch instead" button, shared by [PlaybackPlaceNotice] and the sleep timer prompt. */
@Composable
internal fun PlayOnWatchInsteadButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.secondaryContainer, contentColor = colors.onSecondaryContainer),
        modifier = modifier.fillMaxWidth().height(48.dp),
    ) {
        Icon(Icons.Rounded.Watch, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.play_on_watch_instead), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
