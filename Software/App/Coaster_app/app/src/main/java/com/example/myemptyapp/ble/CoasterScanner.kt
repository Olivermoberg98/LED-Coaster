package com.example.myemptyapp.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.myemptyapp.protocol.CoasterUuids
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A coaster seen advertising during a scan. */
data class ScannedCoaster(val address: String, val name: String)

/** BLE scan that only reports devices advertising the coaster service UUID. */
class CoasterScanner(context: Context) {

    private val adapter = context.applicationContext.getSystemService(BluetoothManager::class.java).adapter

    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    /**
     * Scans while collected and stops when collection ends. Emits every
     * advertisement, so the same coaster can appear many times. The flow fails
     * if Bluetooth is off or the scan can't start.
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun scan(): Flow<ScannedCoaster> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner
            ?: throw IllegalStateException("Bluetooth is off")

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val address = result.device.address
                // The firmware puts the name in the scan response
                val name = result.scanRecord?.deviceName ?: address
                trySend(ScannedCoaster(address, name))
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Scan failed with error $errorCode"))
            }
        }

        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(CoasterUuids.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, callback)
        Log.d(TAG, "Scan started")

        awaitClose {
            try {
                scanner.stopScan(callback)
            } catch (e: Exception) {
                // stopScan throws if Bluetooth was turned off mid-scan
                Log.w(TAG, "stopScan failed", e)
            }
            Log.d(TAG, "Scan stopped")
        }
    }

    private companion object {
        const val TAG = "CoasterScanner"
    }
}
