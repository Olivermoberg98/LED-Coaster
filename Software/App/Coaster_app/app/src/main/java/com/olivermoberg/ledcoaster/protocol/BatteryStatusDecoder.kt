package com.olivermoberg.ledcoaster.protocol

/**
 * Decodes Package 3 as specified under "Coaster → app: battery status" in the
 * root CLAUDE.md. Multi-byte fields are little-endian.
 */
object BatteryStatusDecoder {

    const val TYPE = 0x03
    const val VERSION = 0x01
    const val LENGTH = 13
    private const val PERCENT_UNKNOWN = 0xFF

    sealed interface Result {
        data class Ok(val status: BatteryStatus) : Result
        data class Rejected(val reason: Reason, val detail: String) : Result
    }

    enum class Reason { TOO_SHORT, WRONG_TYPE, UNKNOWN_VERSION, WRONG_LENGTH, BAD_CHECKSUM }

    /** Decodes [packet], stamping it with [receivedAtMs] (phone monotonic time). */
    fun decode(packet: ByteArray, receivedAtMs: Long): Result {
        if (packet.size < 2) return reject(Reason.TOO_SHORT, "${packet.size} bytes")
        val type = packet.u8(0)
        if (type != TYPE) return reject(Reason.WRONG_TYPE, "type 0x%02X".format(type))
        // Checked before the length, so a future version with a different size is reported as such
        val version = packet.u8(1)
        if (version != VERSION) return reject(Reason.UNKNOWN_VERSION, "version $version")
        if (packet.size != LENGTH) return reject(Reason.WRONG_LENGTH, "${packet.size} bytes, expected $LENGTH")

        val expected = (0 until LENGTH - 1).sumOf { packet.u8(it) } and 0xFF
        val received = packet.u8(LENGTH - 1)
        if (expected != received) {
            return reject(Reason.BAD_CHECKSUM, "got 0x%02X, expected 0x%02X".format(received, expected))
        }

        val percent = packet.u8(4)
        return Result.Ok(
            BatteryStatus(
                millivolts = packet.u8(2) or (packet.u8(3) shl 8),
                percent = if (percent == PERCENT_UNKNOWN || percent > 100) null else percent,
                chargerState = ChargerState.fromCode(packet.u8(5)),
                flags = packet.u8(6),
                pins = packet.u8(7),
                uptimeSeconds = packet.u8(8).toLong() or
                    (packet.u8(9).toLong() shl 8) or
                    (packet.u8(10).toLong() shl 16) or
                    (packet.u8(11).toLong() shl 24),
                receivedAtMs = receivedAtMs
            )
        )
    }

    private fun reject(reason: Reason, detail: String) = Result.Rejected(reason, detail)

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF
}
