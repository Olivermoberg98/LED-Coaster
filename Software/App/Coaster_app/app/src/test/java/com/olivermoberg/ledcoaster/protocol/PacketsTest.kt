package com.olivermoberg.ledcoaster.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/**
 * Freezes the exact bytes the firmware accepts. A failure here means the app
 * would no longer talk to deployed coasters.
 */
class PacketsTest {

    private fun bytes(vararg values: Int) = ByteArray(values.size) { values[it].toByte() }

    @Test
    fun package1_allRingCombinations() {
        assertArrayEquals(bytes(0x01, 0x01, 0x01, 0x03), Packets.encodePackage1(outerEnabled = true, innerEnabled = true))
        assertArrayEquals(bytes(0x01, 0x01, 0x00, 0x02), Packets.encodePackage1(outerEnabled = true, innerEnabled = false))
        assertArrayEquals(bytes(0x01, 0x00, 0x01, 0x02), Packets.encodePackage1(outerEnabled = false, innerEnabled = true))
        assertArrayEquals(bytes(0x01, 0x00, 0x00, 0x01), Packets.encodePackage1(outerEnabled = false, innerEnabled = false))
    }

    @Test
    fun package2_fixed() {
        assertArrayEquals(
            bytes(0x02, 0x46, 0x49, 0x58, 0x45, 0x44, 0x2C, 0x30, 0x2C, 0x30, 0x2C, 0x30, 0x86),
            Packets.encodePackage2(Pattern.FIXED, Rgb.OFF)
        )
        assertArrayEquals(
            bytes(0x02, 0x46, 0x49, 0x58, 0x45, 0x44, 0x2C, 0x32, 0x35, 0x35, 0x2C, 0x32, 0x35, 0x35, 0x2C, 0x32, 0x35, 0x35, 0xCA),
            Packets.encodePackage2(Pattern.FIXED, Rgb.WHITE)
        )
    }

    @Test
    fun package2_pulse() {
        assertArrayEquals(
            bytes(0x02, 0x50, 0x55, 0x4C, 0x53, 0x45, 0x2C, 0x30, 0x2C, 0x30, 0x2C, 0x30, 0x9F),
            Packets.encodePackage2(Pattern.PULSE, Rgb.OFF)
        )
        assertArrayEquals(
            bytes(0x02, 0x50, 0x55, 0x4C, 0x53, 0x45, 0x2C, 0x32, 0x35, 0x35, 0x2C, 0x32, 0x35, 0x35, 0x2C, 0x32, 0x35, 0x35, 0xE3),
            Packets.encodePackage2(Pattern.PULSE, Rgb.WHITE)
        )
    }

    @Test
    fun package2_chaser() {
        assertArrayEquals(
            bytes(0x02, 0x43, 0x48, 0x41, 0x53, 0x45, 0x52, 0x2C, 0x30, 0x2C, 0x30, 0x2C, 0x30, 0xCC),
            Packets.encodePackage2(Pattern.CHASER, Rgb.OFF)
        )
        assertArrayEquals(
            bytes(0x02, 0x43, 0x48, 0x41, 0x53, 0x45, 0x52, 0x2C, 0x32, 0x35, 0x35, 0x2C, 0x32, 0x35, 0x35, 0x2C, 0x32, 0x35, 0x35, 0x10),
            Packets.encodePackage2(Pattern.CHASER, Rgb.WHITE)
        )
    }

    @Test
    fun package2_rainbow() {
        assertArrayEquals(
            bytes(0x02, 0x52, 0x41, 0x49, 0x4E, 0x42, 0x4F, 0x57, 0x2C, 0x30, 0x2C, 0x30, 0x2C, 0x30, 0x28),
            Packets.encodePackage2(Pattern.RAINBOW, Rgb.OFF)
        )
        // MainActivity sends this when RAINBOW is picked
        assertArrayEquals(
            bytes(0x02, 0x52, 0x41, 0x49, 0x4E, 0x42, 0x4F, 0x57, 0x2C, 0x30, 0x2C, 0x32, 0x35, 0x35, 0x2C, 0x30, 0x94),
            Packets.encodePackage2(Pattern.RAINBOW, Rgb(0, 255, 0))
        )
    }

    @Test
    fun package2_matchesPreRefactorEncoderForEveryPatternAndManyColours() {
        // The encoder the activities used before the protocol package existed
        fun legacy(mode: String, colors: String): ByteArray {
            val data = byteArrayOf(0x02) + mode.toByteArray() + byteArrayOf(0x2C) + colors.toByteArray()
            return data + (data.sumOf { it.toInt() } % 256).toByte()
        }
        val levels = listOf(0, 1, 9, 10, 99, 100, 127, 128, 200, 254, 255)
        for (pattern in Pattern.entries) {
            for (r in levels) for (g in levels) for (b in levels) {
                val color = Rgb(r, g, b)
                assertArrayEquals(
                    "$pattern $color",
                    legacy(pattern.wireName, "$r,$g,$b"),
                    Packets.encodePackage2(pattern, color)
                )
            }
        }
    }

    @Test
    fun rgb_fromArgbDropsAlpha() {
        assertEquals(Rgb(0x12, 0x34, 0x56), Rgb.fromArgb(0xFF123456.toInt()))
        assertEquals(Rgb(0x12, 0x34, 0x56), Rgb.fromArgb(0x00123456))
    }

    @Test
    fun rgb_rejectsOutOfRangeChannels() {
        assertThrows(IllegalArgumentException::class.java) { Rgb(256, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { Rgb(0, -1, 0) }
    }

    @Test
    fun pattern_wireNamesMatchTheModeDropdown() {
        val xml = File("src/main/res/values/arrays.xml").readText()
        val items = Regex("<item>([^<]+)</item>").findAll(xml).map { it.groupValues[1] }.toList()
        assertEquals(Pattern.entries.map { it.wireName }, items)
    }

    @Test
    fun pattern_fromWireName() {
        assertEquals(Pattern.CHASER, Pattern.fromWireName("CHASER"))
        assertEquals(null, Pattern.fromWireName("chaser"))
    }
}
