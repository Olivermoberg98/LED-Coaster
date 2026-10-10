package com.example.myemptyapp.ble

import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.MainThread

/**
 * App-wide registry of coaster connections, keyed by MAC address, so every
 * screen talks to a coaster through the same [CoasterConnection].
 */
@MainThread
class CoasterRepository(context: Context) {

    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java).adapter
    private val connections = mutableMapOf<String, CoasterConnection>()

    /** Returns the connection for [address], creating it (unconnected) if needed. */
    fun connection(address: String, name: String): CoasterConnection =
        connections.getOrPut(address) {
            CoasterConnection(appContext, adapter.getRemoteDevice(address), name)
        }

    fun connectionOrNull(address: String): CoasterConnection? = connections[address]

    val all: Collection<CoasterConnection> get() = connections.values
}
