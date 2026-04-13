package com.walkingpad.control.ble

object TreadmillController {
    private const val START_BYTE: Byte = 0x6A
    private const val LENGTH: Byte = 0x17
    private const val END_BYTE: Byte = 0x43
    private const val DEFAULT_WEIGHT: Byte = 80 // 0x50
    private const val DEFAULT_USER_ID: Long = 58965456623L

    enum class CommandType(val value: Int) {
        STOP(0),
        PAUSE(2),
        START_OR_SET(4)
    }

    private fun calculateChecksum(packet: ByteArray): Byte {
        var checksum = 0
        for (i in 1..20) {
            checksum = checksum xor (packet[i].toInt() and 0xFF)
        }
        return (checksum and 0xFF).toByte()
    }

    private fun createPacket(
        targetSpeed: Int,
        magicalI11: Int,
        incline: Int,
        cmdType: CommandType,
        isKph: Boolean = true
    ): ByteArray {
        val packet = ByteArray(23)
        packet[0] = START_BYTE
        packet[1] = LENGTH
        // bytes 2-5 are zero
        packet[6] = ((targetSpeed shr 8) and 0xFF).toByte()
        packet[7] = (targetSpeed and 0xFF).toByte()
        packet[8] = (magicalI11 and 0xFF).toByte()
        packet[9] = (incline and 0xFF).toByte()
        packet[10] = DEFAULT_WEIGHT
        packet[11] = 0
        val cmdByte = cmdType.value
        packet[12] = (if (isKph) cmdByte and 0xF7 else cmdByte or 0x08).toByte()

        for (i in 0 until 8) {
            packet[13 + i] = ((DEFAULT_USER_ID shr (56 - i * 8)) and 0xFF).toByte()
        }

        packet[21] = calculateChecksum(packet)
        packet[22] = END_BYTE
        return packet
    }

    fun start(targetSpeed: Int = 1000, incline: Int = 0, isKph: Boolean = true): ByteArray =
        createPacket(targetSpeed, 1, incline, CommandType.START_OR_SET, isKph)

    fun pause(incline: Int = 0, isKph: Boolean = true): ByteArray =
        createPacket(0, 1, incline, CommandType.PAUSE, isKph)

    fun stop(isKph: Boolean = true): ByteArray =
        createPacket(0, 1, 0, CommandType.STOP, isKph)

    fun setSpeed(targetSpeed: Int, incline: Int = 0, isKph: Boolean = true): ByteArray =
        createPacket(targetSpeed, 5, incline, CommandType.START_OR_SET, isKph)

    fun setUnit(isImperial: Boolean): ByteArray {
        val packet = ByteArray(23)
        packet[0] = START_BYTE
        packet[1] = LENGTH
        packet[2] = 0xF3.toByte() // unit change command marker
        packet[12] = if (isImperial) 8 else 0
        for (i in 0 until 8) {
            packet[13 + i] = ((DEFAULT_USER_ID shr (56 - i * 8)) and 0xFF).toByte()
        }
        packet[21] = calculateChecksum(packet)
        packet[22] = END_BYTE
        return packet
    }
}
