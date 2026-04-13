package com.walkingpad.control.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.walkingpad.control.ble.TreadmillData
import com.walkingpad.control.ui.components.ControlButtons
import com.walkingpad.control.ui.components.MetricCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    data: TreadmillData?,
    onDisconnect: () -> Unit,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onSpeedUp: () -> Unit,
    onSpeedDown: () -> Unit,
    onSetSpeed: (Int) -> Unit,
    onToggleUnit: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Walking Pad Control") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    // Unit toggle (km/mi)
                    val unitLabel = if (data?.unitMode == 1) "mi" else "km"
                    TextButton(onClick = onToggleUnit) {
                        Text(
                            unitLabel,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    IconButton(onClick = { showDetails = !showDetails }) {
                        Icon(
                            if (showDetails) Icons.Filled.Dashboard else Icons.Filled.TableChart,
                            contentDescription = "Toggle details"
                        )
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            Icons.Filled.BluetoothDisabled,
                            contentDescription = "Disconnect",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection status chip
            val runningState = data?.runningState ?: 3
            val stateText = data?.displayRunningState ?: "Waiting..."
            val stateColor = when (runningState) {
                1 -> MaterialTheme.colorScheme.primary
                2 -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.outline
            }
            SuggestionChip(
                onClick = {},
                label = { Text(stateText) },
                icon = {
                    Icon(
                        when (runningState) {
                            1 -> Icons.Filled.DirectionsWalk
                            2 -> Icons.Filled.Pause
                            else -> Icons.Filled.FitnessCenter
                        },
                        contentDescription = null,
                        tint = stateColor
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    labelColor = stateColor
                )
            )

            // Metric cards grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "Speed",
                    value = data?.displaySpeed ?: "-",
                    icon = Icons.Filled.Speed,
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Distance",
                    value = data?.displayDistance ?: "-",
                    icon = Icons.Filled.Straighten,
                    modifier = Modifier.weight(1f)
                )
            }

            MetricCard(
                title = "Calories",
                value = data?.displayCalories ?: "-",
                icon = Icons.Filled.LocalFireDepartment,
                modifier = Modifier.fillMaxWidth()
            )

            MetricCard(
                title = "Duration",
                value = data?.displayDuration ?: "-",
                icon = Icons.Filled.Timer,
                modifier = Modifier.fillMaxWidth()
            )

            // Control buttons
            Spacer(modifier = Modifier.height(8.dp))
            ControlButtons(
                runningState = runningState,
                currentSpeed = data?.currentSpeed ?: 0,
                maxSpeed = data?.maxSpeed ?: 6000,
                speedUnit = data?.speedUnit ?: "kph",
                onStart = onStart,
                onPause = onPause,
                onStop = onStop,
                onSpeedUp = onSpeedUp,
                onSpeedDown = onSpeedDown,
                onSetSpeed = onSetSpeed
            )

            // Details table
            if (showDetails && data != null) {
                Spacer(modifier = Modifier.height(8.dp))
                DetailsTable(data)
            }
        }
    }
}

@Composable
private fun DetailsTable(data: TreadmillData) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Detailed Metrics",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            val speedUnit = data.speedUnit
            val distUnit = data.distanceUnit
            val rows = mutableListOf(
                "Cycle ID" to "${data.cycleId}",
                "Running State" to data.displayRunningState,
                "Current Speed" to "${"%.1f".format(data.currentSpeed / 1000.0)} $speedUnit",
                "Target Speed" to "${"%.1f".format(data.targetSpeed / 1000.0)} $speedUnit",
                "Max Speed" to "${"%.1f".format(data.maxSpeed / 1000.0)} $speedUnit",
                "Heart Rate" to "${data.heartRate} bpm",
                "Distance" to "${"%.2f".format(data.distance / 1000.0)} $distUnit",
                "Calories" to "${data.calories} kcal",
                "Duration" to data.displayDuration,
            )
            data.realRotate?.let { rows.add("Motor Speed" to "$it rpm") }
            data.realElectricity?.let { rows.add("Motor Load" to "$it %") }
            data.serialNumber?.let { rows.add("Serial Number" to it) }
            rows.add("Firmware" to "${data.firmwareVersion}")
            rows.add("Device Type" to "${data.deviceType}")
            rows.add("Unit Mode" to if (data.unitMode == 1) "Imperial" else "Metric")
            rows.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            }
        }
    }
}
