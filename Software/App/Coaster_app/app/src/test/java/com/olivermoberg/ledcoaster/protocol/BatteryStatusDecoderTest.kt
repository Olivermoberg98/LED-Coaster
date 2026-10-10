package com.olivermoberg.ledcoaster.protocol

import com.olivermoberg.ledcoaster.protocol.BatteryStatusDecoder.Reason
import com.olivermoberg.ledcoaster.protocol.BatteryStatusDecoder.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatusDecoderTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    /** A version-1 packet with a correct checksum. */
    private fun packet(
        mv: Int = 3794,
        percent: Int = 37,
        charger: Int = 2,
        flags: Int = 0x02,
        pins: Int = 0x04,
        uptime: Long = 1234
    ): ByteArray {
        val body = intArrayOf(
            0x03, 0x01,
            mv and 0xFF, (mv shr 8) and 0xFF,
            percent, charger, flags, pins,
            (uptime and 0xFF).toInt(), ((uptime shr 8) and 0xFF).toInt(),
            ((uptime shr 16) and 0xFF).toInt(), ((uptime shr 24) and 0xFF).toInt()
        )
        return bytes(*body, body.sum() and 0xFF)
    }

    private fun decodeOk(packet: ByteArray, receivedAtMs: Long = 0): BatteryStatus =
        when (val result = BatteryStatusDecoder.decode(packet, receivedAtMs)) {
            is Result.Ok -> result.status
            is Result.Rejected -> throw AssertionError("Rejected: $result")
        }

    private fun rejection(packet: ByteArray): Reason =
        when (val result = BatteryStatusDecoder.decode(packet, 0)) {
            is Result.Ok -> throw AssertionError("Accepted: $result")
            is Result.Rejected -> result.reason
        }

    @Test
    fun decodesTheDocumentedExampleReading() {
        // 3794 mV, 37 %, charging, USB present, PG=0 STAT1=0 STAT2=1, up 1234 s
        val golden = bytes(0x03, 0x01, 0xD2, 0x0E, 0x25, 0x02, 0x02, 0x04, 0xD2, 0x04, 0x00, 0x00, 0xE7)

        assertEquals(
            BatteryStatus(
                millivolts = 3794,
                percent = 37,
                chargerState = ChargerState.CHARGING,
                flags = 0x02,
                pins = 0x04,
                uptimeSeconds = 1234,
                receivedAtMs = 42
            ),
            decodeOk(golden, receivedAtMs = 42)
        )
    }

    @Test
    fun highBitValuesAreReadUnsigned() {
        // mV 0x8001, uptime 0x80000001: the signed-byte traps
        val golden = bytes(0x03, 0x01, 0x01, 0x80, 0xFF, 0x06, 0x07, 0x07, 0x01, 0x00, 0x00, 0x80, 0x19)
        val status = decodeOk(golden)

        assertEquals(0x8001, status.millivolts)
        assertEquals(0x80000001L, status.uptimeSeconds)
        assertEquals(0xFFFF, decodeOk(packet(mv = 0xFFFF)).millivolts)
        assertEquals(0xFFFFFFFFL, decodeOk(packet(uptime = 0xFFFFFFFFL)).uptimeSeconds)
    }

    @Test
    fun everyChargerStateDecodes() {
        val expected = listOf(
            ChargerState.UNKNOWN, ChargerState.ON_BATTERY, ChargerState.CHARGING,
            ChargerState.CHARGE_COMPLETE, ChargerState.LOW_BATTERY,
            ChargerState.TEMPERATURE_FAULT, ChargerState.NO_BATTERY
        )
        expected.forEachIndexed { code, state ->
            assertEquals(state, decodeOk(packet(charger = code)).chargerState)
        }
    }

    @Test
    fun outOfRangeChargerStateIsUnknownButThePacketIsKept() {
        val status = decodeOk(packet(charger = 7, mv = 3700))
        assertEquals(ChargerState.UNKNOWN, status.chargerState)
        assertEquals(3700, status.millivolts)
        assertEquals(ChargerState.UNKNOWN, decodeOk(packet(charger = 0xFF)).chargerState)
    }

    @Test
    fun percentUnknownOrOutOfRangeIsNull() {
        assertNull(decodeOk(packet(percent = 0xFF)).percent)
        assertNull(decodeOk(packet(percent = 101)).percent)
        assertEquals(0, decodeOk(packet(percent = 0)).percent)
        assertEquals(100, decodeOk(packet(percent = 100)).percent)
    }

    @Test
    fun flagBits() {
        val none = decodeOk(packet(flags = 0))
        assertFalse(none.isLowBattery || none.isUsbPowered || none.isFakeData)

        assertTrue(decodeOk(packet(flags = 0x01)).isLowBattery)
        assertTrue(decodeOk(packet(flags = 0x02)).isUsbPowered)
        assertTrue(decodeOk(packet(flags = 0x04)).isFakeData)

        // Reserved bits 3–7 are ignored but kept in the raw byte
        val reserved = decodeOk(packet(flags = 0xF8))
        assertFalse(reserved.isLowBattery || reserved.isUsbPowered || reserved.isFakeData)
        assertEquals(0xF8, reserved.flags)
    }

    @Test
    fun pinsAreKeptRaw() {
        assertEquals(0x07, decodeOk(packet(pins = 0x07)).pins)
        assertEquals(0x00, decodeOk(packet(pins = 0x00)).pins)
    }

    @Test
    fun badChecksumIsRejected() {
        val corrupt = packet().also { it[12] = (it[12] + 1).toByte() }
        assertEquals(Reason.BAD_CHECKSUM, rejection(corrupt))

        val flipped = packet().also { it[2] = (it[2].toInt() xor 0x01).toByte() }
        assertEquals(Reason.BAD_CHECKSUM, rejection(flipped))
    }

    @Test
    fun wrongSizesAreRejected() {
        assertEquals(Reason.TOO_SHORT, rejection(bytes()))
        assertEquals(Reason.TOO_SHORT, rejection(bytes(0x03)))
        assertEquals(Reason.WRONG_LENGTH, rejection(packet().copyOf(12)))
        assertEquals(Reason.WRONG_LENGTH, rejection(packet() + 0x00))
    }

    @Test
    fun wrongTypeIsRejected() {
        // Package 1 and 2 command bytes, and anything else
        for (type in listOf(0x00, 0x01, 0x02, 0x04, 0xFF)) {
            val wrong = packet().also { it[0] = type.toByte() }
            assertEquals("type $type", Reason.WRONG_TYPE, rejection(wrong))
        }
    }

    @Test
    fun unknownVersionIsRejectedWhateverItsLength() {
        for (version in listOf(0x00, 0x02, 0xFF)) {
            val other = packet().also { it[1] = version.toByte() }
            assertEquals(Reason.UNKNOWN_VERSION, rejection(other))
            assertEquals(Reason.UNKNOWN_VERSION, rejection(other + byteArrayOf(0, 0, 0)))
        }
    }
}
