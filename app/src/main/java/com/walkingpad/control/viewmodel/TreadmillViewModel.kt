package com.walkingpad.control.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.walkingpad.control.ble.BluetoothLeManager
import com.walkingpad.control.ble.TreadmillController
import com.walkingpad.control.ble.TreadmillData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TreadmillUiState(
    val isScanning: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val scannedDevices: List<BluetoothLeManager.ScannedDevice> = emptyList(),
    val treadmillData: TreadmillData? = null,
    val error: String? = null,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null
)

class TreadmillViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BluetoothLeManager(application.applicationContext)
    private val prefs = application.getSharedPreferences("walkingpad", Context.MODE_PRIVATE)
    private var userDisconnected = false
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 3
    private val _uiState = MutableStateFlow(TreadmillUiState(
        lastDeviceAddress = prefs.getString("last_device_address", null),
        lastDeviceName = prefs.getString("last_device_name", null)
    ))
    val uiState: StateFlow<TreadmillUiState> = _uiState.asStateFlow()

    init {
        bleManager.onDeviceFound = { device ->
            viewModelScope.launch {
                _uiState.update { state ->
                    if (state.scannedDevices.any { it.address == device.address }) {
                        state
                    } else {
                        state.copy(scannedDevices = state.scannedDevices + device)
                    }
                }
            }
        }

        bleManager.onConnectionStateChanged = { connected ->
            viewModelScope.launch {
                if (connected) {
                    reconnectAttempts = 0
                    _uiState.update { state ->
                        state.copy(isConnected = true, isConnecting = false, error = null)
                    }
                } else {
                    val wasConnected = _uiState.value.isConnected
                    _uiState.update { state ->
                        state.copy(
                            isConnected = false,
                            isConnecting = false,
                            treadmillData = null
                        )
                    }
                    // Auto-reconnect if disconnected unexpectedly
                    if (wasConnected && !userDisconnected) {
                        val address = prefs.getString("last_device_address", null)
                        if (address != null && reconnectAttempts < maxReconnectAttempts) {
                            reconnectAttempts++
                            _uiState.update { it.copy(
                                isConnecting = true,
                                error = "Connection lost, reconnecting (attempt $reconnectAttempts/$maxReconnectAttempts)..."
                            )}
                            delay(2000L)
                            bleManager.connect(address)
                        } else {
                            _uiState.update { it.copy(error = "Disconnected from device") }
                        }
                    }
                    userDisconnected = false
                }
            }
        }

        bleManager.onDataReceived = { data ->
            viewModelScope.launch {
                _uiState.update { it.copy(treadmillData = data) }
            }
        }

        bleManager.onScanComplete = {
            viewModelScope.launch {
                _uiState.update { it.copy(isScanning = false) }
            }
        }

        // Auto reconnect to last device
        val lastAddress = prefs.getString("last_device_address", null)
        if (lastAddress != null && bleManager.isBluetoothEnabled) {
            connect(lastAddress)
        }
    }

    val isBluetoothEnabled: Boolean get() = bleManager.isBluetoothEnabled

    fun startScan() {
        _uiState.update {
            it.copy(isScanning = true, scannedDevices = emptyList(), error = null)
        }
        bleManager.startScan()
    }

    fun stopScan() {
        bleManager.stopScan()
        _uiState.update { it.copy(isScanning = false) }
    }

    fun connect(address: String) {
        _uiState.update { it.copy(isConnecting = true, error = null) }
        bleManager.connect(address)
        // Save as last device
        prefs.edit()
            .putString("last_device_address", address)
            .apply()
        // Save name if we have it from scan results
        val name = _uiState.value.scannedDevices.find { it.address == address }?.name
        if (name != null) {
            prefs.edit().putString("last_device_name", name).apply()
        }
        _uiState.update { it.copy(lastDeviceAddress = address, lastDeviceName = name ?: it.lastDeviceName) }
    }

    fun disconnect() {
        userDisconnected = true
        bleManager.disconnect()
        _uiState.update { it.copy(isConnected = false, treadmillData = null) }
    }

    fun forgetDevice() {
        prefs.edit()
            .remove("last_device_address")
            .remove("last_device_name")
            .apply()
        _uiState.update { it.copy(lastDeviceAddress = null, lastDeviceName = null) }
    }

    fun startTreadmill() {
        val data = _uiState.value.treadmillData
        val speed = when {
            data != null && data.targetSpeed > 0 -> data.targetSpeed
            data != null && data.currentSpeed > 0 -> data.currentSpeed
            else -> 1000
        }
        bleManager.sendCommand(TreadmillController.start(targetSpeed = speed))
    }

    fun pauseTreadmill() {
        bleManager.sendCommand(TreadmillController.pause())
    }

    fun stopTreadmill() {
        bleManager.sendCommand(TreadmillController.stop())
    }

    fun speedUp() {
        val currentSpeed = _uiState.value.treadmillData?.currentSpeed ?: return
        bleManager.sendCommand(TreadmillController.setSpeed(currentSpeed + 100))
    }

    fun setSpeed(rawSpeed: Int) {
        bleManager.sendCommand(TreadmillController.setSpeed(rawSpeed))
    }

    fun speedDown() {
        val currentSpeed = _uiState.value.treadmillData?.currentSpeed ?: return
        if (currentSpeed > 100) {
            bleManager.sendCommand(TreadmillController.setSpeed(currentSpeed - 100))
        }
    }

    fun toggleUnit() {
        val data = _uiState.value.treadmillData ?: return
        val isCurrentlyMetric = data.unitMode == 0
        bleManager.sendCommand(TreadmillController.setUnit(isImperial = isCurrentlyMetric))
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    override fun onCleared() {
        super.onCleared()
        bleManager.destroy()
    }
}
