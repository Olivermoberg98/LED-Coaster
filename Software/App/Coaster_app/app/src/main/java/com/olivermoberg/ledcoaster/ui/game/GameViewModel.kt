package com.olivermoberg.ledcoaster.ui.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.olivermoberg.ledcoaster.CoasterApp
import com.olivermoberg.ledcoaster.ble.CoasterConnection
import com.olivermoberg.ledcoaster.games.Game
import com.olivermoberg.ledcoaster.games.NattDuellen
import com.olivermoberg.ledcoaster.games.RandomDrink
import com.olivermoberg.ledcoaster.ui.BatteryView
import com.olivermoberg.ledcoaster.ui.lowBatteryMessage
import com.olivermoberg.ledcoaster.ui.monotonicTicker
import com.olivermoberg.ledcoaster.ui.toView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** State of the games screen: the circles and which coaster sits on each. */
@OptIn(ExperimentalCoroutinesApi::class)
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

    /** Circle position → battery of the coaster on it; circles without data are absent. */
    val batteries: StateFlow<Map<Int, BatteryView>> = combine(
        _assignments.flatMapLatest { placed ->
            if (placed.isEmpty()) return@flatMapLatest flowOf(emptyMap())
            combine(placed.map { (position, coaster) -> coaster.batteryStatus.map { position to it } }) { pairs ->
                pairs.mapNotNull { (position, status) -> status?.let { position to it } }.toMap()
            }
        },
        monotonicTicker()
    ) { statuses, now -> statuses.mapValues { it.value.toView(now) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Low-battery warnings for the placed coasters; collect only while the screen is visible. */
    val lowBatteryWarnings: Flow<String> = _assignments.flatMapLatest { placed ->
        placed.values.map { coaster ->
            coaster.lowBatteryWarnings.map { lowBatteryMessage(coaster.name, it) }
        }.merge()
    }

    enum class GameMode { NATT_DUELLEN, RANDOM_DRINK }

    sealed interface GameStatus {
        data object Waiting : GameStatus
        data class Running(val mode: GameMode) : GameStatus
        data object Over : GameStatus
    }

    private val _gameStatus = MutableStateFlow<GameStatus>(GameStatus.Waiting)
    val gameStatus: StateFlow<GameStatus> = _gameStatus.asStateFlow()
    private var gameJob: Job? = null

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-off messages for the user, shown as toasts. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    /** Changes the number of circles, disconnecting coasters on circles that disappear. */
    fun setCircleCount(count: Int) {
        if (count == _circleCount.value) return
        val removed = _assignments.value.filterKeys { it >= count }.values
        _assignments.value = _assignments.value.filterKeys { it < count }
        _circleCount.value = count
        if (removed.isNotEmpty()) stopGame()
        removed.forEach { it.disconnect() }
        if (removed.isNotEmpty()) _messages.trySend("${removed.size} device(s) disconnected")
    }

    /**
     * Places [coaster] on the circle at [position] and connects it. A coaster
     * already on that circle is removed and disconnected.
     */
    fun assign(position: Int, coaster: CoasterConnection) {
        if (coaster in _assignments.value.values) {
            _messages.trySend("${coaster.name} is already placed in another circle!")
            return
        }
        _assignments.value[position]?.let { replaced ->
            stopGame()
            replaced.disconnect()
            _messages.trySend("${replaced.name} replaced by ${coaster.name}")
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
        stopGame()
        _assignments.value = _assignments.value - position
        _messages.trySend("${coaster.name} removed")
        coaster.disconnect()
    }

    /** True when every circle has a coaster and all of them are ready. */
    fun allCirclesReady(): Boolean =
        _assignments.value.size == _circleCount.value && _assignments.value.values.all { it.isReady }

    /** Starts [mode] on the placed coasters, replacing any game already running. */
    fun startGame(mode: GameMode) {
        if (!allCirclesReady()) {
            _messages.trySend("Please connect devices to all circles before starting!")
            return
        }
        stopGame()
        val game: Game = when (mode) {
            GameMode.NATT_DUELLEN -> NattDuellen()
            GameMode.RANDOM_DRINK -> RandomDrink()
        }
        // Games run on a snapshot, so later circle changes can't empty it mid-round
        val players = _assignments.value.toSortedMap().values.toList()
        if (mode == GameMode.NATT_DUELLEN) _messages.trySend("Starting Mode 1!")
        gameJob = viewModelScope.launch {
            _gameStatus.value = GameStatus.Running(mode)
            try {
                val loser = game.play(players)
                if (loser != null) _messages.send("Game Over! ${loser.name} loses!")
                _gameStatus.value = GameStatus.Over
            } catch (e: CancellationException) {
                _gameStatus.value = GameStatus.Waiting
                throw e
            }
        }
    }

    /** Stops the running game, if any. Coasters keep whatever they last showed. */
    fun stopGame() {
        if (gameJob?.isActive == true) _messages.trySend("Game stopped")
        gameJob?.cancel()
        gameJob = null
    }
}
