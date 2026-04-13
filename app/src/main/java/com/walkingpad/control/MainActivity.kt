package com.walkingpad.control

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.walkingpad.control.ui.screens.DashboardScreen
import com.walkingpad.control.ui.screens.ScanScreen
import com.walkingpad.control.ui.theme.WalkingPadTheme
import com.walkingpad.control.viewmodel.TreadmillViewModel

class MainActivity : ComponentActivity() {

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* permissions granted or denied */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request permissions if needed
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }

        setContent {
            WalkingPadTheme {
                val viewModel: TreadmillViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                // Keep screen on while connected
                val activity = this@MainActivity
                DisposableEffect(uiState.isConnected) {
                    if (uiState.isConnected) {
                        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                if (uiState.isConnected) {
                    DashboardScreen(
                        data = uiState.treadmillData,
                        onDisconnect = viewModel::disconnect,
                        onStart = viewModel::startTreadmill,
                        onPause = viewModel::pauseTreadmill,
                        onStop = viewModel::stopTreadmill,
                        onSpeedUp = viewModel::speedUp,
                        onSpeedDown = viewModel::speedDown,
                        onSetSpeed = viewModel::setSpeed,
                        onToggleUnit = viewModel::toggleUnit
                    )
                } else {
                    ScanScreen(
                        devices = uiState.scannedDevices,
                        isScanning = uiState.isScanning,
                        isConnecting = uiState.isConnecting,
                        bluetoothEnabled = viewModel.isBluetoothEnabled,
                        error = uiState.error,
                        lastDeviceAddress = uiState.lastDeviceAddress,
                        lastDeviceName = uiState.lastDeviceName,
                        onScan = viewModel::startScan,
                        onStopScan = viewModel::stopScan,
                        onConnect = viewModel::connect,
                        onDismissError = viewModel::clearError,
                        onForgetDevice = viewModel::forgetDevice
                    )
                }
            }
        }
    }
}
