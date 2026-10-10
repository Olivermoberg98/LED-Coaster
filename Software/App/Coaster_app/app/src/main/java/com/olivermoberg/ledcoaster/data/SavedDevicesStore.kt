package com.olivermoberg.ledcoaster.data

import android.content.Context
import androidx.core.content.edit

/** A coaster the user has connected to before. */
data class SavedDevice(val address: String, val name: String)

/**
 * Coasters the user has connected to, persisted as address → name in the
 * `BluetoothDevices` SharedPreferences. The address is the key, so two coasters
 * that share a name are still told apart.
 */
class SavedDevicesStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** All saved coasters, sorted by name. */
    fun all(): List<SavedDevice> =
        prefs.all.mapNotNull { (address, name) -> (name as? String)?.let { SavedDevice(address, it) } }
            .sortedBy { it.name }

    fun contains(address: String): Boolean = prefs.contains(address)

    fun save(device: SavedDevice) {
        prefs.edit { putString(device.address, device.name) }
    }

    private companion object {
        const val PREFS_NAME = "BluetoothDevices"
    }
}
