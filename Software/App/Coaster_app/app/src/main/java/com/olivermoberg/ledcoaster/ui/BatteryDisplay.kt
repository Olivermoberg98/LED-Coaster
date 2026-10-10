package com.olivermoberg.ledcoaster.ui

import android.os.SystemClock
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.olivermoberg.ledcoaster.protocol.BatteryStatus
import com.olivermoberg.ledcoaster.protocol.ChargerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** The coaster pushes every 30 s; three missed pushes make its status stale. */
const val STALE_AFTER_MS = 90_000L

/** A battery status as the screens show it. */
data class BatteryView(val status: BatteryStatus, val stale: Boolean)

fun BatteryStatus.isStale(nowMs: Long): Boolean = nowMs - receivedAtMs > STALE_AFTER_MS

fun BatteryStatus.toView(nowMs: Long) = BatteryView(this, isStale(nowMs))

/** `82%`, or `?` when the coaster doesn't know. */
fun BatteryStatus.percentText(): String = percent?.let { "$it%" } ?: "?"

/** Percent for a game circle, with `⚡` while charging. */
fun BatteryStatus.circleText(): String =
    percentText() + if (chargerState == ChargerState.CHARGING) "⚡" else ""

fun ChargerState.label(): String = when (this) {
    ChargerState.UNKNOWN -> "Unknown"
    ChargerState.ON_BATTERY -> "On battery"
    ChargerState.CHARGING -> "Charging"
    ChargerState.CHARGE_COMPLETE -> "Charged"
    ChargerState.LOW_BATTERY -> "Low battery"
    ChargerState.TEMPERATURE_FAULT -> "Temperature fault"
    ChargerState.NO_BATTERY -> "No battery"
}

/** `Coaster-05 · 82% · Charging`, or `Coaster-05 · no battery data` before the first packet. */
fun statusLine(name: String, status: BatteryStatus?): String = when {
    status == null -> "$name · no battery data"
    else -> "$name · ${status.percentText()} · ${status.chargerState.label()}" +
        if (status.isFakeData) " (test data)" else ""
}

fun lowBatteryMessage(name: String, status: BatteryStatus): String =
    "$name: battery low (${status.percentText()})"

/** Phone monotonic time, emitted every [periodMs]. */
fun monotonicTicker(periodMs: Long = 1_000L): Flow<Long> = flow {
    while (true) {
        emit(SystemClock.elapsedRealtime())
        delay(periodMs)
    }
}

/** Shows each of [warnings] as a Snackbar, collecting only while the screen is started. */
@Composable
fun BatteryWarningHost(warnings: Flow<String>, modifier: Modifier = Modifier) {
    val hostState = remember { SnackbarHostState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(warnings, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            warnings.collect {
                hostState.showSnackbar(it, withDismissAction = true, duration = SnackbarDuration.Long)
            }
        }
    }
    SnackbarHost(hostState, modifier)
}
