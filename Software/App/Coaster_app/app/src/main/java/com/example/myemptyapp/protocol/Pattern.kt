package com.example.myemptyapp.protocol

/**
 * LED patterns the firmware understands. [wireName] must match
 * `stringToPatternType` in the firmware's `patterns.cpp`; the firmware falls
 * back to FIXED for any name it doesn't know.
 */
enum class Pattern(val wireName: String) {
    FIXED("FIXED"),
    PULSE("PULSE"),
    CHASER("CHASER"),
    RAINBOW("RAINBOW");

    companion object {
        fun fromWireName(name: String): Pattern? = entries.firstOrNull { it.wireName == name }
    }
}
