package com.walkingpad.control.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("MissingPermission")
class BluetoothLeManager(private val context: Context) {

    companion object {
        private const val TAG = "BLEManager"
        val NOTIFY_CHAR_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
        val WRITE_CHAR_UUID: UUID = UUID.fromString("0000ff01-0000-1000-8000-00805f9b34fb")
        private val HEARTBEAT_HEAD = byteArrayOf(0x4d, 0x00)
        private val HEARTBEAT_BODY = byteArrayOf(0x05, 0x6a, 0x05, 0xfd.toByte(), 0xf8.toByte(), 0x43)
        private val CCC_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val SCAN_TIMEOUT_MS = 10000L
        private const val DESIRED_MTU = 512
    }

    data class ScannedDevice(val name: String?, val address: String, val rssi: Int)

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var heartbeatCounter = 0
    private val pendingCommands = ConcurrentLinkedQueue<ByteArray>()
    private val handler = Handler(Looper.getMainLooper())
    private val isWriting = AtomicBoolean(false)
    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()

    // Fragment reassembly buffer
    private val fragmentBuffer = ByteArrayOutputStream()

    var onDeviceFound: ((ScannedDevice) -> Unit)? = null
    var onConnectionStateChanged: ((Boolean) -> Unit)? = null
    var onDataReceived: ((TreadmillData) -> Unit)? = null
    var onScanComplete: (() -> Unit)? = null

    val isBluetoothEnabled: Boolean get() = bluetoothAdapter?.isEnabled == true

    // --- Scanning ---

    private var scanner: BluetoothLeScanner? = null
    private var isScanning = false

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: return
            if (!name.startsWith("PitPat", ignoreCase = true)) return
            onDeviceFound?.invoke(
                ScannedDevice(
                    name = name,
                    address = device.address,
                    rssi = result.rssi
                )
            )
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            isScanning = false
        }
    }

    fun startScan() {
        if (isScanning) return
        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "Scanner not available")
            return
        }
        isScanning = true
        scanner?.startScan(scanCallback)
        handler.postDelayed({
            stopScan()
            onScanComplete?.invoke()
        }, SCAN_TIMEOUT_MS)
    }

    fun stopScan() {
        if (!isScanning) return
        scanner?.stopScan(scanCallback)
        isScanning = false
    }

    // --- Connection ---

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected, requesting MTU $DESIRED_MTU...")
                    gatt.requestMtu(DESIRED_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected (status=$status)")
                    cleanup()
                    handler.post { onConnectionStateChanged?.invoke(false) }
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.i(TAG, "MTU changed to $mtu (status=$status), discovering services...")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed: $status")
                disconnect()
                return
            }

            var writeChar: BluetoothGattCharacteristic? = null
            var notifyChar: BluetoothGattCharacteristic? = null

            for (service in gatt.services) {
                Log.d(TAG, "Service: ${service.uuid}")
                for (char in service.characteristics) {
                    Log.d(TAG, "  Char: ${char.uuid} props=${char.properties}")
                    if (char.uuid == WRITE_CHAR_UUID) writeChar = char
                    if (char.uuid == NOTIFY_CHAR_UUID) notifyChar = char
                }
            }

            if (writeChar == null || notifyChar == null) {
                Log.e(TAG, "Required characteristics not found")
                disconnect()
                return
            }

            writeCharacteristic = writeChar

            gatt.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(CCC_DESCRIPTOR_UUID)
            if (descriptor != null) {
                Log.i(TAG, "Writing notification descriptor...")
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                Log.w(TAG, "No CCC descriptor")
                handler.post { onConnectionStateChanged?.invoke(true) }
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (descriptor.uuid == CCC_DESCRIPTOR_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Notifications enabled, ready!")
                    handler.post { onConnectionStateChanged?.invoke(true) }
                } else {
                    Log.e(TAG, "Failed to enable notifications: $status")
                    disconnect()
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(value)
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            characteristic.value?.let { handleNotification(it) }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Write failed: $status")
            }
            isWriting.set(false)
            processWriteQueue()
        }
    }

    private fun handleNotification(value: ByteArray) {
        if (value.size < 4) return

        val type = value[1].toInt() and 0xFF
        val payloadData = value.copyOfRange(4, value.size)

        Log.d(TAG, "Notify [${value.size}] type=0x%02X: %s".format(
            type, value.joinToString(" ") { "%02X".format(it) }
        ))

        // Check if this is a multi-fragment message or single message
        // Type 0x04 or small type values indicate final/only fragment
        // Type 0x14+ indicates more fragments to follow
        if (type > 0x10) {
            // More fragments expected - accumulate
            fragmentBuffer.write(payloadData)
        } else {
            // Final or only fragment
            fragmentBuffer.write(payloadData)
            val assembled = fragmentBuffer.toByteArray()
            fragmentBuffer.reset()

            val data = TreadmillData.fromPayload(assembled)
            if (data.checksumValid) {
                handler.post { onDataReceived?.invoke(data) }
            }

            // Only send heartbeat after complete message
            sendHeartbeat()
        }
    }

    private fun cleanup() {
        bluetoothGatt = null
        writeCharacteristic = null
        heartbeatCounter = 0
        pendingCommands.clear()
        writeQueue.clear()
        fragmentBuffer.reset()
        isWriting.set(false)
    }

    fun connect(address: String) {
        stopScan()
        val device = bluetoothAdapter?.getRemoteDevice(address)
        if (device == null) {
            Log.e(TAG, "Device not found: $address")
            return
        }
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        bluetoothGatt?.let {
            it.disconnect()
            it.close()
        }
        cleanup()
    }

    // --- Write queue ---

    private fun queueWrite(data: ByteArray) {
        writeQueue.add(data)
        processWriteQueue()
    }

    private fun processWriteQueue() {
        if (!isWriting.compareAndSet(false, true)) return
        val data = writeQueue.poll()
        if (data == null) {
            isWriting.set(false)
            return
        }
        val gatt = bluetoothGatt
        val char = writeCharacteristic
        if (gatt == null || char == null) {
            isWriting.set(false)
            return
        }
        Log.d(TAG, "Write [${data.size}]: ${data.joinToString(" ") { "%02X".format(it) }}")
        val result = gatt.writeCharacteristic(char, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        if (result != BluetoothStatusCodes.SUCCESS) {
            Log.e(TAG, "writeCharacteristic failed: $result")
            isWriting.set(false)
        }
    }

    // --- Heartbeat ---

    private fun sendHeartbeat() {
        val counter = (heartbeatCounter and 0xFF).toByte()
        heartbeatCounter = (heartbeatCounter + 1) % 256

        val pendingData = pendingCommands.poll()

        val dataToSend = if (pendingData != null) {
            HEARTBEAT_HEAD + byteArrayOf(counter, (pendingData.size and 0xFF).toByte()) + pendingData
        } else {
            HEARTBEAT_HEAD + byteArrayOf(counter) + HEARTBEAT_BODY
        }

        queueWrite(dataToSend)
    }

    fun sendCommand(data: ByteArray) {
        pendingCommands.add(data)
    }

    fun destroy() {
        stopScan()
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }
}
