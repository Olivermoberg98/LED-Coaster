package com.olivermoberg.ledcoaster.protocol

import java.util.UUID

/** GATT identifiers of the coaster firmware (`BLEHandler.cpp`). */
object CoasterUuids {
    val SERVICE: UUID = UUID.fromString("00001801-0000-1000-8000-008051234567")

    /** App → coaster commands (Package 1 and 2), WRITE. */
    val COMMAND: UUID = UUID.fromString("00001234-0000-1000-8000-001122334455")

    /** Coaster → app battery status (Package 3), READ | NOTIFY. */
    val STATUS: UUID = UUID.fromString("00001235-0000-1000-8000-001122334455")
}
