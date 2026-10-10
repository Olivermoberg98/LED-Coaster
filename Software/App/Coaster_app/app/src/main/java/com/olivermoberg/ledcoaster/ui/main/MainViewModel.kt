package com.olivermoberg.ledcoaster.ui.main

import android.Manifest
import android.app.Application
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olivermoberg.ledcoaster.CoasterApp
import com.olivermoberg.ledcoaster.ble.CoasterConnection
import com.olivermoberg.ledcoaster.ble.ScannedCoaster
import com.olivermoberg.ledcoaster.data.SavedDevice
import com.olivermoberg.ledcoaster.protocol.Pattern
import com.olivermoberg.ledcoaster.protocol.Rgb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** State of the single-coaster control screen. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CoasterApp

    private val _savedDevices = MutableStateFlow(app.savedDevices.all())
    val savedDevices: StateFlow<List<SavedDevice>> = _savedDevices.asStateFlow()

    /** Coasters seen in the current or last scan that are not saved yet, sorted by name. */
    private val _scanResults = MutableStateFlow<List<ScannedCoaster>>(emptyList())
    val scanResults: StateFlow<List<ScannedCoaster>> = _scanResults.asStateFlow()
    private var scanJob: Job? = null

    /** The coaster this screen controls. */
    private val current = MutableStateFlow<CoasterConnection?>(null)

    val isReady: StateFlow<Boolean> = current
        .flatMapLatest { it?.state ?: flowOf(CoasterConnection.State.DISCONNECTED) }
        .map { it == CoasterConnection.State.READY }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Name of the coaster this screen controls, or null before one is chosen. */
    val currentName: StateFlow<String?> = current
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _outerEnabled = MutableStateFlow(true)
    val outerEnabled: StateFlow<Boolean> = _outerEnabled.asStateFlow()
    private val _innerEnabled = MutableStateFlow(true)
    val innerEnabled: StateFlow<Boolean> = _innerEnabled.asStateFlow()

    private val _pattern = MutableStateFlow(Pattern.FIXED)
    val pattern: StateFlow<Pattern> = _pattern.asStateFlow()

    /** Last colour picked on the colour wheel as ARGB, or null before the first pick. */
    private val _pickedColor = MutableStateFlow<Int?>(null)
    val pickedColor: StateFlow<Int?> = _pickedColor.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the user, shown as toasts. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    val isBluetoothEnabled: Boolean get() = app.scanner.isBluetoothEnabled

    init {
        // Report connects and disconnects of the current coaster
        viewModelScope.launch {
            current.collectLatest { connection ->
                if (connection == null) return@collectLatest
                var wasReady = false
                connection.state.collect { state ->
                    val ready = state == CoasterConnection.State.READY
                    if (ready && !wasReady) _messages.send("Connected to ${connection.name}")
                    if (!ready && wasReady) _messages.send("Disconnected from ${connection.name}")
                    wasReady = ready
                }
            }
        }
    }

    /** Scans for [SCAN_DURATION_MS]. Does nothing if a scan is already running. */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        if (scanJob?.isActive == true) return
        val scan = app.scanner.scan()
        scanJob = viewModelScope.launch {
            withTimeoutOrNull(SCAN_DURATION_MS) {
                scan
                    .catch { e ->
                        Log.w(TAG, "Scan ended with error", e)
                        _messages.send("Could not scan for coasters")
                    }
                    .collect { device -> onDeviceScanned(device) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private fun onDeviceScanned(device: ScannedCoaster) {
        if (app.savedDevices.contains(device.address)) return
        if (_scanResults.value.any { it.address == device.address }) return
        _scanResults.value = (_scanResults.value + device).sortedBy { it.name }
    }

    /** Saves the coaster if it's new, makes it the current one and connects. */
    fun connect(address: String, name: String) {
        // An active scan slows connection setup
        stopScan()

        if (!app.savedDevices.contains(address)) {
            app.savedDevices.save(SavedDevice(address, name))
            _savedDevices.value = app.savedDevices.all()
        }

        val connection = app.repository.connection(address, name)
        current.value = connection
        viewModelScope.launch {
            if (!connection.connect() && current.value == connection) {
                _messages.send("Could not connect to ${connection.name}")
            }
        }
    }

    fun disconnect() {
        val connection = current.value
        if (connection == null || connection.state.value == CoasterConnection.State.DISCONNECTED) {
            _messages.trySend("No device is currently connected")
        } else {
            connection.disconnect()
        }
    }

    fun setOuterEnabled(enabled: Boolean) {
        _outerEnabled.value = enabled
        sendRings()
    }

    fun setInnerEnabled(enabled: Boolean) {
        _innerEnabled.value = enabled
        sendRings()
    }

    private fun sendRings() {
        current.value?.sendPackage1(_outerEnabled.value, _innerEnabled.value)
    }

    /** RAINBOW is sent at once; the other patterns wait for a colour to be picked. */
    fun selectPattern(pattern: Pattern) {
        _pattern.value = pattern
        if (pattern == Pattern.RAINBOW) {
            current.value?.sendPackage2(Pattern.RAINBOW, RAINBOW_COLOR)
        }
    }

    /** Sends the selected pattern with [argb], a colour from the colour wheel. */
    fun pickColor(argb: Int) {
        _pickedColor.value = argb
        current.value?.sendPackage2(_pattern.value, Rgb.fromArgb(argb))
    }

    /** Runs when the main screen finishes, i.e. the user leaves the app. */
    override fun onCleared() {
        app.repository.disconnectAll()
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val SCAN_DURATION_MS = 10_000L
        val RAINBOW_COLOR = Rgb(0, 255, 0)
    }
}
