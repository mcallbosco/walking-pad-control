package com.walkingpad.control.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun ControlButtons(
    runningState: Int,
    currentSpeed: Int,
    maxSpeed: Int,
    speedUnit: String,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedUp: () -> Unit,
    onSpeedDown: () -> Unit,
    onSetSpeed: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // runningState: 0=Starting, 1=Running, 2=Paused, 3=Stopped
    val isRunning = runningState == 1
    val isPaused = runningState == 2
    val isStopped = runningState == 3

    val minSpeed = 500f   // 0.5 kph/mph
    val maxSpeedF = maxOf(maxSpeed.toFloat(), 1000f)

    // Track slider interaction state
    var isDragging by remember { mutableStateOf(false) }
    var sliderValue by remember { mutableFloatStateOf(currentSpeed.toFloat()) }
    // Target speed the user set via slider; 0 = not waiting for ramp-up
    var dragTargetSpeed by remember { mutableIntStateOf(0) }

    // Sync slider to treadmill speed, unless user is dragging or treadmill
    // is still ramping toward the user's chosen speed
    if (!isDragging) {
        if (dragTargetSpeed > 0) {
            // Hold slider at target until treadmill reaches it (within 0.1 kph)
            if (kotlin.math.abs(currentSpeed - dragTargetSpeed) <= 100) {
                dragTargetSpeed = 0
                sliderValue = currentSpeed.toFloat()
            }
        } else {
            sliderValue = currentSpeed.toFloat()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Speed slider
        if (isRunning) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Speed",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${"%.1f".format(sliderValue / 1000.0)} $speedUnit",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = {
                            isDragging = true
                            // Snap to 0.1 increments (100 raw units)
                            sliderValue = (it / 100).roundToInt() * 100f
                        },
                        onValueChangeFinished = {
                            isDragging = false
                            dragTargetSpeed = sliderValue.roundToInt()
                            onSetSpeed(dragTargetSpeed)
                        },
                        valueRange = minSpeed..maxSpeedF,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${"%.1f".format(minSpeed / 1000.0)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${"%.1f".format(maxSpeedF / 1000.0)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // +/- buttons and play/pause
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onSpeedDown,
                enabled = isRunning,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Speed Down")
            }

            if (isRunning) {
                FilledIconButton(
                    onClick = onPause,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = "Pause",
                        modifier = Modifier.size(36.dp)
                    )
                }
            } else {
                FilledIconButton(
                    onClick = onStart,
                    enabled = isPaused || isStopped,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Start",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            FilledTonalIconButton(
                onClick = onSpeedUp,
                enabled = isRunning,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Speed Up")
            }
        }

        // Stop button
        OutlinedButton(
            onClick = onStop,
            enabled = isPaused,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(
                Icons.Filled.Stop,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stop")
        }
    }
}
