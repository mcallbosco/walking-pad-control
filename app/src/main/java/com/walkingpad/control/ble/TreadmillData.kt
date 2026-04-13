package com.walkingpad.control.ble

data class TreadmillData(
    val currentSpeed: Int = 0,
    val targetSpeed: Int = 0,
    val distance: Long = 0,
    val currentIncline: Int = 0,
    val targetIncline: Int = 0,
    val heartRate: Int = 0,
    val steps: Long = 0,
    val calories: Int = 0,
    val duration: Long = 0,
    val durationSeconds: Float = 0f,
    val cycleId: Int = 0,
    val firmwareVersion: Int = 0,
    val maxSpeed: Int = 0,
    val maxIncline: Int = 0,
    val deviceType: Int = 0,
    val runWalkState: Int = 0,
    val serialNumber: String? = null,
    val sensorStatus: Int = 0,
    val carryingIdler: Int = 0,
    val battery: Int? = null,
    val unitMode: Int = 0,         // 0 = metric, 1 = imperial
    val isConnected: Int = 0,
    val runningState: Int = 3,     // 0=Starting, 1=Running, 2=Paused, 3=Stopped
    val hasBracelet: Boolean = false,
    val realElectricity: Int? = null,
    val realRotate: Int? = null,
    val realElectricitySteps: Int? = null,
    val bleModel: Int? = null,
    val bleBrand: Int? = null,
    val checksumValid: Boolean = false,
    val payloadHex: String = ""
) {
    val speedUnit: String get() = if (unitMode == 1) "mph" else "kph"
    val distanceUnit: String get() = if (unitMode == 1) "mi" else "km"
    val displaySpeed: String get() = "%.1f %s".format(currentSpeed / 1000.0, speedUnit)
    val displayDistance: String get() = "%.2f %s".format(distance / 1000.0, distanceUnit)
    val displayCalories: String get() = "$calories kcal"
    val displaySteps: String get() {
        val s = realElectricitySteps ?: steps.toInt()
        return if (s > 0) "$s" else "-"
    }
    val displayDuration: String get() {
        val totalSec = durationSeconds.toLong()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }
    val displayRunningState: String get() = when (runningState) {
        0 -> "Starting"
        1 -> "Running"
        2 -> "Paused"
        3 -> "Stopped"
        else -> "Unknown"
    }

    companion object {
        fun fromPayload(payload: ByteArray, isFirst: Boolean = false): TreadmillData {
            val hex = payload.joinToString(" ") { "%02X".format(it) }

            if (payload.size < 31) {
                return TreadmillData(payloadHex = hex)
            }

            // Validate checksum
            val calcChecksum = calcChecksum(payload, payload.size - 2)
            val valid = calcChecksum == (payload[payload.size - 2].toInt() and 0xFF)
            if (!valid) {
                return TreadmillData(payloadHex = hex)
            }

            val length = payload[1].toInt() and 0xFF
            val currentSpeed = ((payload[3].toInt() and 0xFF) shl 8 or (payload[4].toInt() and 0xFF)) and 0xFFFF
            val targetSpeed = ((payload[5].toInt() and 0xFF) shl 8 or (payload[6].toInt() and 0xFF)) and 0xFFFF
            val distance = ((payload[7].toLong() and 0xFF) shl 24 or
                    ((payload[8].toLong() and 0xFF) shl 16) or
                    ((payload[9].toLong() and 0xFF) shl 8) or
                    (payload[10].toLong() and 0xFF)) and 0xFFFFFFFFL
            val currentIncline = payload[11].toInt() and 0xFF
            val targetIncline = minOf(payload[12].toInt() and 0xFF, 192)
            val heartRate = payload[13].toInt() and 0xFF
            val steps = ((payload[14].toLong() and 0xFF) shl 24 or
                    ((payload[15].toLong() and 0xFF) shl 16) or
                    ((payload[16].toLong() and 0xFF) shl 8) or
                    (payload[17].toLong() and 0xFF)) and 0xFFFFFFFFL
            val calories = ((payload[18].toInt() and 0xFF) shl 8 or (payload[19].toInt() and 0xFF)) and 0xFFFF
            val durationRaw = ((payload[20].toLong() and 0xFF) shl 24 or
                    ((payload[21].toLong() and 0xFF) shl 16) or
                    ((payload[22].toLong() and 0xFF) shl 8) or
                    (payload[23].toLong() and 0xFF)) and 0xFFFFFFFFL
            val cycleId = payload[24].toInt() and 0xFF
            val firmwareVersion = payload[25].toInt() and 0xFF
            val flags = payload[26].toInt() and 0xFF
            val maxSpeed = ((payload[27].toInt() and 0xFF) shl 8 or (payload[28].toInt() and 0xFF)) and 0xFFFF
            val maxIncline = payload[29].toInt() and 0xFF
            val deviceType = payload[30].toInt() and 31
            val runWalkState = (payload[12].toInt() shr 6) and 3

            // Extract flags
            val unitMode = if (flags and 128 == 128) 1 else 0
            val isConnected = if (flags and 1 == 1) 1 else 0
            val stateBits = flags and 24
            val runningState = when (stateBits) {
                24 -> 0  // Starting
                8 -> 1   // Running
                16 -> 2  // Paused
                else -> 3 // Stopped
            }
            val hasBracelet = ((flags and 96) shr 5) != 3
            val durationSeconds = if (firmwareVersion > 19) durationRaw / 1000f else durationRaw.toFloat()

            // Extended data parsing
            var serialNumber: String? = null
            var sensorStatus = 0
            var carryingIdler = 0
            var realElectricity: Int? = null
            var realRotate: Int? = null
            var realElectricitySteps: Int? = null
            var battery: Int? = null
            var bleModel: Int? = null
            var bleBrand: Int? = null

            if (firmwareVersion < 25 || isFirst) {
                val minLength = if (firmwareVersion > 5) 48 else 46
                val start = if (firmwareVersion > 5) 32 else 30
                if (payload.size >= minLength && isFirst) {
                    serialNumber = payload.sliceArray(start until start + 16)
                        .toString(Charsets.US_ASCII).trim('\u0000')
                }
            } else {
                if (payload.size >= 52) {
                    bleModel = payload[48].toInt() and 0xFF
                    bleBrand = payload[49].toInt() and 0xFF
                    serialNumber = payload.sliceArray(32 until 48)
                        .toString(Charsets.US_ASCII).trim('\u0000')
                } else {
                    if (length > 32) carryingIdler = payload[32].toInt() and 0xFF
                    if (length > 35) sensorStatus = payload[33].toInt() and 0xFF
                    if (length > 42) {
                        realElectricity = payload[40].toInt() and 0xFF
                        realRotate = ((payload[41].toInt() and 0xFF) shl 8 or (payload[42].toInt() and 0xFF)) and 0xFFFF
                    }
                    if (length > 44) {
                        realElectricitySteps = ((payload[43].toInt() and 0xFF) shl 8 or (payload[44].toInt() and 0xFF)) and 0xFFFF
                    }
                    if (length > 46) {
                        battery = payload[45].toInt() and 0xFF
                    }
                }
            }

            return TreadmillData(
                currentSpeed = currentSpeed,
                targetSpeed = targetSpeed,
                distance = distance,
                currentIncline = currentIncline,
                targetIncline = targetIncline,
                heartRate = heartRate,
                steps = steps,
                calories = calories,
                duration = durationRaw,
                durationSeconds = durationSeconds,
                cycleId = cycleId,
                firmwareVersion = firmwareVersion,
                maxSpeed = maxSpeed,
                maxIncline = maxIncline,
                deviceType = deviceType,
                runWalkState = runWalkState,
                serialNumber = serialNumber,
                sensorStatus = sensorStatus,
                carryingIdler = carryingIdler,
                battery = battery,
                unitMode = unitMode,
                isConnected = isConnected,
                runningState = runningState,
                hasBracelet = hasBracelet,
                realElectricity = realElectricity,
                realRotate = realRotate,
                realElectricitySteps = realElectricitySteps,
                bleModel = bleModel,
                bleBrand = bleBrand,
                checksumValid = true,
                payloadHex = hex
            )
        }

        private fun calcChecksum(payload: ByteArray, length: Int): Int {
            var checksum = payload[1].toInt() and 0xFF
            for (i in 2 until length) {
                checksum = checksum xor (payload[i].toInt() and 0xFF)
            }
            return checksum
        }
    }
}
