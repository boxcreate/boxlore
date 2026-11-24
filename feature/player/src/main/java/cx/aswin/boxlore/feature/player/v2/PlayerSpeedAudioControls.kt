package cx.aswin.boxlore.feature.player.v2

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.text.asString
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.playback.PlayerState
import cx.aswin.boxlore.feature.player.R
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

/** What the speed controls show about trim silence and the playing show's own speed. */
data class PlayerSpeedAudio(
    val showTitle: String = "",
    val hasShowSpeed: Boolean = false,
    val trimSilence: Boolean = false,
    val hasShowTrimSilence: Boolean = false,
    val isCasting: Boolean = false,
) {
    companion object {
        fun from(state: PlayerState): PlayerSpeedAudio = PlayerSpeedAudio(
            showTitle = state.currentPodcast?.title.orEmpty(),
            hasShowSpeed = state.audio.hasShowSpeed,
            trimSilence = state.audio.trimSilence,
            hasShowTrimSilence = state.audio.hasShowTrimSilence,
            isCasting = state.playbackRoute.isRemote,
        )
    }
}

/** "Saved for The Daily", or "Saved for this show" when the title isn't known yet. */
internal fun savedForShowLabel(showTitle: String): UiText = if (showTitle.isBlank()) {
    UiText.res(R.string.player_saved_for_this_show)
} else {
    UiText.res(R.string.player_saved_for_show, showTitle)
}

/** The line under Trim silence: casting first, since nothing is trimmed on a Cast device. */
internal fun trimSilenceSupportingText(audio: PlayerSpeedAudio): UiText = when {
    audio.isCasting -> UiText.res(R.string.player_not_available_casting)
    audio.hasShowTrimSilence -> savedForShowLabel(audio.showTitle)
    else -> UiText.res(R.string.player_trim_silence_hint)
}

/** The speed chip's hint: highlighted for a custom speed or while silence is trimmed. */
internal fun speedChipIsActive(speed: Float, audio: PlayerSpeedAudio): Boolean =
    kotlin.math.abs(speed - 1f) > 0.001f || (audio.trimSilence && !audio.isCasting)

/** The speed sheet's lines below the speeds: the show's own speed, then the Trim silence switch. */
@Composable
internal fun SpeedSheetAudioSection(
    audio: PlayerSpeedAudio,
    colorScheme: ColorScheme,
    onUseAppSpeed: () -> Unit,
    onTrimSilenceChange: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (audio.hasShowSpeed) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = savedForShowLabel(audio.showTitle).asString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onUseAppSpeed) { Text(stringResource(R.string.player_use_app_speed)) }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(colorScheme.surfaceContainerHigh)
                .toggleable(
                    value = audio.trimSilence && !audio.isCasting,
                    enabled = !audio.isCasting,
                    role = Role.Switch,
                    onValueChange = onTrimSilenceChange,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.player_trim_silence),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = GoogleSansWeight.semiBold,
                    color = colorScheme.onSurface,
                )
                Text(
                    text = trimSilenceSupportingText(audio).asString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Switch(
                checked = audio.trimSilence && !audio.isCasting,
                onCheckedChange = null,
                enabled = !audio.isCasting,
                colors = SwitchDefaults.colors(checkedTrackColor = colorScheme.primary, checkedThumbColor = colorScheme.onPrimary),
            )
        }
    }
}

/** The inline speed controls' last row: Trim silence, and Use app speed while the show keeps its own. */
@Composable
internal fun InlineSpeedAudioRow(
    audio: PlayerSpeedAudio,
    colorScheme: ColorScheme,
    shapes: InlineSpeedAudioShapes,
    onUseAppSpeed: () -> Unit,
    onTrimSilenceChange: (Boolean) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val trimOn = audio.trimSilence && !audio.isCasting
        val trimState = stringResource(if (trimOn) R.string.player_on else R.string.player_off)
        InlineAudioTile(
            tile = InlineAudioTileState(
                stringResource(R.string.player_trim_silence),
                trimSilenceSupportingText(audio).asString(),
                active = trimOn,
                enabled = !audio.isCasting,
            ),
            colorScheme = colorScheme,
            shape = if (audio.hasShowSpeed) shapes.start else shapes.full,
            onClick = { onTrimSilenceChange(!audio.trimSilence) },
            modifier = Modifier
                .weight(1f)
                .semantics {
                    role = Role.Switch
                    stateDescription = trimState
                },
        )
        if (audio.hasShowSpeed) {
            InlineAudioTile(
                tile = InlineAudioTileState(
                    stringResource(R.string.player_use_app_speed),
                    stringResource(R.string.player_speed_saved_for_show),
                ),
                colorScheme = colorScheme,
                shape = shapes.end,
                onClick = onUseAppSpeed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

internal data class InlineSpeedAudioShapes(val start: Shape, val end: Shape, val full: Shape)

/** The inline speed controls' trim silence state and actions. */
internal data class InlineSpeedAudio(
    val state: PlayerSpeedAudio = PlayerSpeedAudio(),
    val onUseAppSpeed: () -> Unit = {},
    val onTrimSilenceChange: (Boolean) -> Unit = {},
)

/** One tile across the whole last row: both bottom corners round, like the group's bottom ends. */
internal val InlineSpeedAudioBottomShape: Shape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = 9.dp,
    smoothnessAsPercentTL = 60,
    cornerRadiusTR = 9.dp,
    smoothnessAsPercentTR = 60,
    cornerRadiusBL = 24.dp,
    smoothnessAsPercentBL = 60,
    cornerRadiusBR = 24.dp,
    smoothnessAsPercentBR = 60,
)

private data class InlineAudioTileState(val title: String, val subtitle: String, val active: Boolean = false, val enabled: Boolean = true)

@Composable
private fun InlineAudioTile(
    tile: InlineAudioTileState,
    colorScheme: ColorScheme,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = tile.active
    val enabled = tile.enabled
    val tileDescription = stringResource(R.string.player_tile_description, tile.title, tile.subtitle)
    val container by animateColorAsState(
        targetValue = if (active) colorScheme.primary else colorScheme.surfaceContainerHigh,
        animationSpec = tween(180),
        label = "inlineAudioContainer",
    )
    val content by animateColorAsState(
        targetValue = when {
            active -> colorScheme.onPrimary
            enabled -> colorScheme.onSurfaceVariant
            else -> colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
        },
        animationSpec = tween(180),
        label = "inlineAudioContent",
    )
    Column(
        modifier = modifier
            .height(56.dp)
            .quickActionPress(enabled = enabled, shape = shape, onClick = onClick)
            .clip(shape)
            .background(container)
            .semantics(mergeDescendants = true) { contentDescription = tileDescription }
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tile.title,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = GoogleSansWeight.bold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = tile.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
