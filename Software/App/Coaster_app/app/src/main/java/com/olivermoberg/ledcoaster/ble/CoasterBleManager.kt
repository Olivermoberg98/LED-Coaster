package com.olivermoberg.ledcoaster.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import com.olivermoberg.ledcoaster.protocol.CoasterUuids
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.WriteRequest

/**
 * GATT client for one coaster. Every operation goes through the library's
 * request queue, so writes are never dropped because another one is in flight.
 */
internal class CoasterBleManager(context: Context) : BleManager(context) {

    private var command: BluetoothGattCharacteristic? = null

    /** Battery status (Package 3). Optional: firmware before Package 3 lacks it. */
    private var status: BluetoothGattCharacteristic? = null

    /** Receives every raw status packet, from both the initial read and notifications. */
    var onStatusPacket: ((ByteArray) -> Unit)? = null

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        val service = gatt.getService(CoasterUuids.SERVICE)
        command = service?.getCharacteristic(CoasterUuids.COMMAND)
        status = service?.getCharacteristic(CoasterUuids.STATUS)
            ?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
        if (command != null && status == null) Log.i(TAG, "No battery status characteristic; older firmware")
        return command != null
    }

    override fun initialize() {
        val status = status ?: return
        setNotificationCallback(status).with { _, data -> data.value?.let { onStatusPacket?.invoke(it) } }
        // Subscribe first, then read, so no packet pushed in between is missed
        enableNotifications(status)
            .fail { _, code -> Log.w(TAG, "Enabling status notifications failed, status $code") }
            .enqueue()
        readCharacteristic(status)
            .with { _, data -> data.value?.let { onStatusPacket?.invoke(it) } }
            .fail { _, code -> Log.w(TAG, "Reading status failed, status $code") }
            .enqueue()
    }

    override fun onServicesInvalidated() {
        command = null
        status = null
    }

    /** Queues a write-with-response to the command characteristic. */
    fun writeCommand(packet: ByteArray): WriteRequest =
        writeCharacteristic(command, packet, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)

    override fun getMinLogPriority(): Int = Log.INFO

    override fun log(priority: Int, message: String) {
        Log.println(priority, TAG, message)
    }

    private companion object {
        const val TAG = "CoasterBle"
    }
}
