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

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        command = gatt.getService(CoasterUuids.SERVICE)?.getCharacteristic(CoasterUuids.COMMAND)
        return command != null
    }

    override fun onServicesInvalidated() {
        command = null
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
