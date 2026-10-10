package com.olivermoberg.ledcoaster.protocol

/** Charger state reported in Package 3 byte 5; codes match `ChargerState` in the firmware's `main.cpp`. */
enum class ChargerState(val code: Int) {
    UNKNOWN(0),
    ON_BATTERY(1),
    CHARGING(2),
    CHARGE_COMPLETE(3),
    LOW_BATTERY(4),
    TEMPERATURE_FAULT(5),
    NO_BATTERY(6);

    companion object {
        /** Codes this app doesn't know map to [UNKNOWN]. */
        fun fromCode(code: Int): ChargerState = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

/** One decoded Package 3 (coaster → app battery status). */
data class BatteryStatus(
    val millivolts: Int,
    /** 0–100, or null when the coaster reports it as unknown. */
    val percent: Int?,
    val chargerState: ChargerState,
    /** Raw flags byte; see [isLowBattery], [isUsbPowered], [isFakeData]. */
    val flags: Int,
    /** Raw status-pin byte: bit 0 `PG`, bit 1 `STAT1`, bit 2 `STAT2` (pin levels). */
    val pins: Int,
    /** Seconds since the coaster booted; a drop means it reset. */
    val uptimeSeconds: Long,
    /** Phone monotonic time (`SystemClock.elapsedRealtime`) when the packet arrived. */
    val receivedAtMs: Long
) {
    /** Set by the firmware on LBO or below its warning voltage. */
    val isLowBattery: Boolean get() = flags and FLAG_LOW_BATTERY != 0
    val isUsbPowered: Boolean get() = flags and FLAG_USB_POWER != 0
    /** Set by the `battery-fake` firmware build. */
    val isFakeData: Boolean get() = flags and FLAG_FAKE_DATA != 0

    companion object {
        const val FLAG_LOW_BATTERY = 0x01
        const val FLAG_USB_POWER = 0x02
        const val FLAG_FAKE_DATA = 0x04
    }
}
