package com.olivermoberg.ledcoaster.protocol

/** An LED colour, each channel 0–255. */
data class Rgb(val red: Int, val green: Int, val blue: Int) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255) { "Channel out of range: $this" }
    }

    /** ASCII form the firmware parses: `"R,G,B"`. */
    fun toWire(): String = "$red,$green,$blue"

    companion object {
        /** Games turn a coaster "off" by sending FIXED with this colour. */
        val OFF = Rgb(0, 0, 0)
        val WHITE = Rgb(255, 255, 255)

        fun fromArgb(color: Int) = Rgb((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)
    }
}

/** Encoders for the app → coaster packets. See "The BLE contract" in the root CLAUDE.md. */
object Packets {
    private const val PACKAGE_1_COMMAND: Byte = 0x01
    private const val PACKAGE_2_COMMAND: Byte = 0x02
    private const val SEPARATOR: Byte = 0x2C // ','

    /** Package 1, ring enable: `[0x01, outer, inner, checksum]`. */
    fun encodePackage1(outerEnabled: Boolean, innerEnabled: Boolean): ByteArray =
        withChecksum(
            byteArrayOf(
                PACKAGE_1_COMMAND,
                if (outerEnabled) 0x01 else 0x00,
                if (innerEnabled) 0x01 else 0x00
            )
        )

    /** Package 2, pattern and colour: `[0x02] + "PATTERN" + ',' + "R,G,B" + checksum`. */
    fun encodePackage2(pattern: Pattern, color: Rgb): ByteArray =
        withChecksum(
            byteArrayOf(PACKAGE_2_COMMAND) +
                pattern.wireName.toByteArray(Charsets.US_ASCII) +
                SEPARATOR +
                color.toWire().toByteArray(Charsets.US_ASCII)
        )

    /** Appends the sum of all bytes, mod 256. */
    private fun withChecksum(data: ByteArray): ByteArray =
        data + data.sumOf { it.toInt() and 0xFF }.toByte()
}
