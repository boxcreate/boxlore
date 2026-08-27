package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.playback.PlaybackRouteState

internal enum class CastHeroDisplayMode {
    ARTWORK,
    CAST_CONTROLS,
    TRANSCRIPT,
}

internal fun resolveCastHeroDisplayMode(
    isRemote: Boolean,
    showInlineTranscript: Boolean,
    showCastControls: Boolean,
): CastHeroDisplayMode =
    when {
        showInlineTranscript -> CastHeroDisplayMode.TRANSCRIPT
        isRemote && showCastControls -> CastHeroDisplayMode.CAST_CONTROLS
        else -> CastHeroDisplayMode.ARTWORK
    }

internal fun canSkipFromCastHero(nextEpisodeId: String?): Boolean = !nextEpisodeId.isNullOrBlank()

internal data class FullPlayerCastHeroModel(
    val route: PlaybackRouteState,
    val dimensions: HeroDimensions,
    val canSkipNext: Boolean,
    val colorScheme: ColorScheme,
)

internal data class FullPlayerCastHeroActions(
    val onVolumeChange: (Int) -> Unit,
    val onChangeDevice: () -> Unit,
    val onSkipNext: () -> Unit,
    val onStopCasting: () -> Unit,
)

@Composable
internal fun FullPlayerCastHero(
    model: FullPlayerCastHeroModel,
    actions: FullPlayerCastHeroActions,
    modifier: Modifier = Modifier,
) {
    var pendingVolume by remember(model.route.volume) {
        mutableFloatStateOf(model.route.volume.toFloat())
    }
    MaterialTheme(colorScheme = model.colorScheme) {
        Surface(
            modifier =
                modifier
                    .width(model.dimensions.width)
                    .height(model.dimensions.height)
                    .shadow(
                        elevation = 12.dp,
                        shape = MaterialTheme.shapes.extraLarge,
                        clip = false,
                    ),
            shape = MaterialTheme.shapes.extraLarge,
            color = model.colorScheme.primaryContainer,
            contentColor = model.colorScheme.onPrimaryContainer,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = MaterialTheme.shapes.large,
                        color = model.colorScheme.primary,
                        contentColor = model.colorScheme.onPrimary,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CastConnected,
                            contentDescription = null,
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .padding(start = 12.dp),
                    ) {
                        Text(
                            text = "PLAYING ON",
                            style = MaterialTheme.typography.labelSmall,
                            color = model.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = model.route.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                if (model.route.canControlVolume) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        color = model.colorScheme.surfaceContainerHigh,
                        contentColor = model.colorScheme.onSurface,
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Slider(
                                value = pendingVolume,
                                onValueChange = { pendingVolume = it },
                                onValueChangeFinished = {
                                    actions.onVolumeChange(pendingVolume.toInt())
                                },
                                valueRange = model.route.minimumVolume.toFloat()..model.route.maximumVolume.toFloat(),
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilledTonalIconButton(onClick = actions.onChangeDevice) {
                            Icon(
                                imageVector = Icons.Rounded.CastConnected,
                                contentDescription = "Change device",
                            )
                        }
                        Text("Device", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilledTonalIconButton(
                            onClick = actions.onSkipNext,
                            enabled = model.canSkipNext,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = "Next episode",
                            )
                        }
                        Text("Next", style = MaterialTheme.typography.labelSmall)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        FilledTonalIconButton(onClick = actions.onStopCasting) {
                            Icon(
                                imageVector = Icons.Rounded.StopCircle,
                                contentDescription = "Stop casting",
                            )
                        }
                        Text("Stop", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
