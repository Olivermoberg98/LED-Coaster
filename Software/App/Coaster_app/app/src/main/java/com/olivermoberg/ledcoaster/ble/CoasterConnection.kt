package com.olivermoberg.ledcoaster.ble

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import com.olivermoberg.ledcoaster.games.CoasterController
import com.olivermoberg.ledcoaster.protocol.BatteryStatus
import com.olivermoberg.ledcoaster.protocol.BatteryStatusDecoder
import com.olivermoberg.ledcoaster.protocol.Packets
import com.olivermoberg.ledcoaster.protocol.Pattern
import com.olivermoberg.ledcoaster.protocol.Rgb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import no.nordicsemi.android.ble.ktx.suspend
import no.nordicsemi.android.ble.observer.ConnectionObserver

/**
 * The single connection to one coaster, shared by every screen through
 * [CoasterRepository]. It stays open until [disconnect] is called.
 */
class CoasterConnection internal constructor(
    private val context: Context,
    private val device: BluetoothDevice,
    /** Advertised name, e.g. `Coaster-05`. */
    override val name: String,
    /** Monotonic clock stamped on each battery status. */
    private val clock: () -> Long = SystemClock::elapsedRealtime
) : CoasterController {
    enum class State { DISCONNECTED, CONNECTING, READY, DISCONNECTING }

    val address: String get() = device.address

    /** The ID shown on game circles: the part of [name] after the last '-'. */
    val coasterId: String get() = name.substringAfterLast('-')

    /**
     * When true, [connect] waits for the coaster to appear and the link is
     * re-established automatically after a link loss.
     */
    var autoReconnect = false

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    val isReady: Boolean get() = _state.value == State.READY

    private val _batteryStatus = MutableStateFlow<BatteryStatus?>(null)
    /**
     * Latest valid Package 3 from this coaster. Null until the first one
     * arrives after a connect, after a disconnect, and always for firmware
     * without the status characteristic.
     */
    val batteryStatus: StateFlow<BatteryStatus?> = _batteryStatus.asStateFlow()

    private val manager = CoasterBleManager(context).apply {
        connectionObserver = object : ConnectionObserver {
            override fun onDeviceConnecting(device: BluetoothDevice) { _state.value = State.CONNECTING }
            override fun onDeviceConnected(device: BluetoothDevice) { _state.value = State.CONNECTING }
            override fun onDeviceFailedToConnect(device: BluetoothDevice, reason: Int) {
                _state.value = State.DISCONNECTED
                _batteryStatus.value = null
            }
            override fun onDeviceReady(device: BluetoothDevice) { _state.value = State.READY }
            override fun onDeviceDisconnecting(device: BluetoothDevice) { _state.value = State.DISCONNECTING }
            override fun onDeviceDisconnected(device: BluetoothDevice, reason: Int) {
                _state.value = State.DISCONNECTED
                _batteryStatus.value = null
            }
        }
        onStatusPacket = ::onStatusPacket
    }

    private fun onStatusPacket(packet: ByteArray) {
        when (val result = BatteryStatusDecoder.decode(packet, clock())) {
            is BatteryStatusDecoder.Result.Ok -> {
                val status = result.status
                Log.i(
                    TAG,
                    "$name battery: ${status.millivolts} mV, ${status.percent ?: "?"}%, " +
                        "${status.chargerState}, flags 0x%02X, pins 0x%02X, up ${status.uptimeSeconds} s"
                            .format(status.flags, status.pins)
                )
                _batteryStatus.value = status
            }
            is BatteryStatusDecoder.Result.Rejected ->
                Log.w(TAG, "$name: status packet rejected, ${result.reason}: ${result.detail}")
        }
    }

    /**
     * Connects and completes once services are discovered and the coaster is
     * ready for commands. Returns false if the connection could not be made,
     * including when BLUETOOTH_CONNECT has not been granted.
     */
    suspend fun connect(): Boolean {
        if (isReady) return true
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_CONNECT not granted, not connecting to $name ($address)")
            return false
        }
        val request = manager.connect(device)
        if (autoReconnect) {
            request.useAutoConnect(true)
        } else {
            // Absorbs the transient GATT 133 errors common on a first connect. The
            // timeout covers a coaster in slow advertising (one packet per ~2 s).
            request.retry(3, 100).timeout(CONNECT_TIMEOUT_MS)
        }
        return try {
            request.suspend()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Connect to $name ($address) failed", e)
            false
        }
    }

    fun disconnect() {
        manager.disconnect().enqueue()
    }

    /** Queues Package 1 (ring enable). Returns immediately. */
    fun sendPackage1(outerEnabled: Boolean, innerEnabled: Boolean) =
        send(Packets.encodePackage1(outerEnabled, innerEnabled))

    /** Queues Package 2 (pattern and colour). Returns immediately. */
    fun sendPackage2(pattern: Pattern, color: Rgb) =
        send(Packets.encodePackage2(pattern, color))

    override suspend fun showColor(color: Rgb): Boolean =
        write(Packets.encodePackage2(Pattern.FIXED, color))

    /** Writes [packet] and suspends until the coaster acknowledges it. */
    private suspend fun write(packet: ByteArray): Boolean {
        if (!isReady) {
            Log.w(TAG, "Not ready, dropping packet for $name ($address)")
            return false
        }
        return try {
            manager.writeCommand(packet).suspend()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Write to $name ($address) failed", e)
            false
        }
    }

    private fun send(packet: ByteArray) {
        if (!isReady) {
            Log.w(TAG, "Not ready, dropping packet for $name ($address)")
            return
        }
        manager.writeCommand(packet)
            .fail { _, status -> Log.e(TAG, "Write to $name ($address) failed, status $status") }
            .enqueue()
    }

    private companion object {
        const val TAG = "CoasterConnection"
        const val CONNECT_TIMEOUT_MS = 15_000L
    }
}
