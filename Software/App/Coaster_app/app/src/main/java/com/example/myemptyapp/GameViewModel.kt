package com.example.myemptyapp

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myemptyapp.ble.CoasterConnection
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** State of the games screen: the circles and which coaster sits on each. */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CoasterApp

    /** Every saved coaster, offered for dragging onto a circle. */
    val coasters: List<CoasterConnection> =
        app.savedDevices.all().map { app.repository.connection(it.address, it.name) }

    private val _circleCount = MutableStateFlow(1)
    val circleCount: StateFlow<Int> = _circleCount.asStateFlow()

    /** Circle position → the coaster placed on it. */
    private val _assignments = MutableStateFlow<Map<Int, CoasterConnection>>(emptyMap())
    val assignments: StateFlow<Map<Int, CoasterConnection>> = _assignments.asStateFlow()

    val assignedCoasters: Set<CoasterConnection> get() = _assignments.value.values.toSet()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the user, shown as toasts. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Changes the number of circles, disconnecting coasters on circles that disappear. */
    fun setCircleCount(count: Int) {
        if (count == _circleCount.value) return
        val removed = _assignments.value.filterKeys { it >= count }.values
        _assignments.value = _assignments.value.filterKeys { it < count }
        _circleCount.value = count
        removed.forEach { it.disconnect() }
        if (removed.isNotEmpty()) _messages.trySend("${removed.size} device(s) disconnected")
    }

    /** Places [coaster] on the circle at [position] and connects it. */
    fun assign(position: Int, coaster: CoasterConnection) {
        if (coaster in _assignments.value.values) {
            _messages.trySend("${coaster.name} is already placed in another circle!")
            return
        }
        _assignments.value = _assignments.value + (position to coaster)
        viewModelScope.launch {
            if (coaster.connect()) return@launch
            _messages.send("Could not connect to ${coaster.name}")
            if (_assignments.value[position] == coaster) {
                _assignments.value = _assignments.value - position
            }
        }
    }

    /** Removes the coaster from the circle at [position] and disconnects it. */
    fun unassign(position: Int) {
        val coaster = _assignments.value[position] ?: return
        _assignments.value = _assignments.value - position
        _messages.trySend("${coaster.name} removed")
        coaster.disconnect()
    }

    /** True when every circle has a coaster and all of them are ready. */
    fun allCirclesReady(): Boolean =
        _assignments.value.size == _circleCount.value && _assignments.value.values.all { it.isReady }
}
