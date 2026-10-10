package com.olivermoberg.ledcoaster.ui

import com.olivermoberg.ledcoaster.protocol.BatteryStatus
import com.olivermoberg.ledcoaster.protocol.ChargerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryDisplayTest {

    private fun status(
        percent: Int? = 82,
        state: ChargerState = ChargerState.ON_BATTERY,
        flags: Int = 0,
        receivedAtMs: Long = 1_000L,
    ) = BatteryStatus(3794, percent, state, flags, 0, 600, receivedAtMs)

    @Test
    fun staleOnlyAfterNinetySeconds() {
        val s = status(receivedAtMs = 1_000L)
        assertFalse(s.isStale(1_000L))
        assertFalse(s.isStale(1_000L + STALE_AFTER_MS))
        assertTrue(s.isStale(1_000L + STALE_AFTER_MS + 1))
        assertEquals(BatteryView(s, stale = true), s.toView(1_000L + STALE_AFTER_MS + 1))
    }

    @Test
    fun unknownPercentShowsQuestionMark() {
        assertEquals("82%", status().percentText())
        assertEquals("?", status(percent = null).percentText())
    }

    @Test
    fun circleTextMarksChargingOnly() {
        assertEquals("82%⚡", status(state = ChargerState.CHARGING).circleText())
        assertEquals("82%", status(state = ChargerState.CHARGE_COMPLETE).circleText())
    }

    @Test
    fun statusLine() {
        assertEquals("Coaster-05 · 82% · Charging", statusLine("Coaster-05", status(state = ChargerState.CHARGING)))
        assertEquals("Coaster-05 · no battery data", statusLine("Coaster-05", null))
        assertEquals(
            "Coaster-05 · ? · Unknown (test data)",
            statusLine("Coaster-05", status(percent = null, state = ChargerState.UNKNOWN, flags = BatteryStatus.FLAG_FAKE_DATA))
        )
    }

    @Test
    fun everyChargerStateHasALabel() {
        assertEquals(ChargerState.entries.size, ChargerState.entries.map { it.label() }.toSet().size)
    }

    @Test
    fun lowBatteryMessage() {
        assertEquals("Coaster-05: battery low (4%)", lowBatteryMessage("Coaster-05", status(percent = 4)))
    }
}
