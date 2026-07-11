package cx.aswin.boxcast.feature.player.v2.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cx.aswin.boxcast.core.model.SleepTimerConstants
import cx.aswin.boxcast.feature.player.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SpeedSleepSheet(
    playbackSpeed: Float,
    sleepTimerEnd: Long?,
    colorScheme: ColorScheme,
    onSpeedChange: (Float) -> Unit,
    onSleepClick: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedOptions = remember {
        listOf(0.5f to "0.5x", 0.8f to "0.8x", 1.0f to "1x", 1.25f to "1.25x", 1.5f to "1.5x")
    }
    val sleepOptions = remember {
        listOf(
            0 to "Off",
            15 to "15m",
            30 to "30m",
            60 to "1 hr",
            120 to "2 hr",
            SleepTimerConstants.END_OF_EPISODE_MINUTES to "End",
        )
    }

    var remainingTime by remember { mutableStateOf("") }
    val isTimerActive = sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()

    LaunchedEffect(sleepTimerEnd) {
        if (sleepTimerEnd != null && sleepTimerEnd > System.currentTimeMillis()) {
            while (isActive) {
                val left = sleepTimerEnd - System.currentTimeMillis()
                if (left <= 0) {
                    remainingTime = ""
                    break
                }
                remainingTime = formatTime(left)
                delay(1000)
            }
        } else {
            remainingTime = ""
        }
    }

    PlayerSheetScaffold(
        colorScheme = colorScheme,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Speed & Sleep",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = colorScheme.onSurface,
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SpeedSleepSection(
                title = "Playback speed",
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = colorScheme.primary,
                    )
                },
                colorScheme = colorScheme,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    speedOptions.forEach { (speed, label) ->
                        val selected = speed == playbackSpeed
                        AssistChip(
                            onClick = { onSpeedChange(speed) },
                            label = { Text(label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) {
                                    colorScheme.primaryContainer
                                } else {
                                    colorScheme.surfaceContainerHighest
                                },
                                labelColor = if (selected) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onSurface
                                },
                            ),
                        )
                    }
                }
            }

            SpeedSleepSection(
                title = if (isTimerActive && remainingTime.isNotEmpty()) {
                    "Sleep timer • $remainingTime"
                } else {
                    "Sleep timer"
                },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.NightsStay,
                        contentDescription = null,
                        tint = colorScheme.primary,
                    )
                },
                colorScheme = colorScheme,
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sleepOptions.forEach { (minutes, label) ->
                        val selected = if (minutes == 0) !isTimerActive else false
                        AssistChip(
                            onClick = { onSleepClick(minutes) },
                            label = { Text(label) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (selected) {
                                    colorScheme.primaryContainer
                                } else {
                                    colorScheme.surfaceContainerHighest
                                },
                                labelColor = if (selected) {
                                    colorScheme.onPrimaryContainer
                                } else {
                                    colorScheme.onSurface
                                },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedSleepSection(
    title: String,
    leadingIcon: @Composable () -> Unit,
    colorScheme: ColorScheme,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leadingIcon()
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}
