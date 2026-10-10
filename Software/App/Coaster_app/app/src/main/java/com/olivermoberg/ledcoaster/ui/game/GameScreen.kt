package com.olivermoberg.ledcoaster.ui.game

import android.content.ClipData
import android.content.ClipDescription
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olivermoberg.ledcoaster.R
import com.olivermoberg.ledcoaster.ble.CoasterConnection
import com.olivermoberg.ledcoaster.protocol.BatteryStatus
import com.olivermoberg.ledcoaster.protocol.ChargerState
import com.olivermoberg.ledcoaster.ui.BatteryView
import com.olivermoberg.ledcoaster.ui.BatteryWarningHost
import com.olivermoberg.ledcoaster.ui.CoasterTheme
import com.olivermoberg.ledcoaster.ui.LowBattery
import com.olivermoberg.ledcoaster.ui.ScreenBackground
import com.olivermoberg.ledcoaster.ui.circleText
import com.olivermoberg.ledcoaster.ui.main.Dropdown
import com.olivermoberg.ledcoaster.ui.main.Section

private const val MAX_CIRCLES = 10
private val EmptyCircle = Color(0xFFFF00FF)
private val AssignedCircle = Color(0xFFFFBB33)

/** A saved coaster as the list shows it. */
private data class CoasterItem(val address: String, val name: String, val state: CoasterConnection.State)

/** The games screen, wired to [viewModel]. */
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val circleCount by viewModel.circleCount.collectAsStateWithLifecycle()
    val assignments by viewModel.assignments.collectAsStateWithLifecycle()
    val gameStatus by viewModel.gameStatus.collectAsStateWithLifecycle()
    val batteries by viewModel.batteries.collectAsStateWithLifecycle()
    val coasters = viewModel.coasters.map { coaster ->
        CoasterItem(coaster.address, coaster.name, coaster.state.collectAsStateWithLifecycle().value)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GameContent(
            coasters = coasters,
            circles = List(circleCount) { assignments[it]?.coasterId },
            batteries = batteries,
            gameStatus = gameStatus,
            onCircleCountSelected = viewModel::setCircleCount,
            onDrop = { position, address ->
                viewModel.coasters.find { it.address == address }?.let { viewModel.assign(position, it) }
            },
            onUnassign = viewModel::unassign,
            onStartGame = viewModel::startGame,
        )
        BatteryWarningHost(viewModel.lowBatteryWarnings, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * The screen as plain state. [circles] holds the coaster ID on each circle,
 * or null for an empty one, and [batteries] the battery by circle position. A coaster is long-pressed and dragged onto a
 * circle; a circle is long-pressed to empty it.
 */
@Composable
private fun GameContent(
    coasters: List<CoasterItem>,
    circles: List<String?>,
    batteries: Map<Int, BatteryView>,
    gameStatus: GameViewModel.GameStatus,
    onCircleCountSelected: (Int) -> Unit,
    onDrop: (position: Int, address: String) -> Unit,
    onUnassign: (position: Int) -> Unit,
    onStartGame: (GameViewModel.GameMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Section("COASTERS") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Circles", fontSize = 18.sp, modifier = Modifier.weight(1f))
                Box(modifier = Modifier.width(96.dp)) {
                    Dropdown(
                        label = circles.size.toString(),
                        items = (1..MAX_CIRCLES).map { it.toString() },
                        enabled = true,
                        onSelected = { index -> onCircleCountSelected(index + 1) },
                    )
                }
            }
            coasters.forEach { CoasterRow(it) }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                var first = 0
                for (rowSize in circleRows(circles.size)) {
                    val start = first
                    Row(modifier = Modifier.padding(vertical = 8.dp)) {
                        for (position in start until start + rowSize) {
                            Circle(
                                coasterId = circles[position],
                                battery = batteries[position],
                                onDrop = { address -> onDrop(position, address) },
                                onLongPress = { onUnassign(position) },
                            )
                        }
                    }
                    first += rowSize
                }
            }
        }

        Section("GAMES") {
            // Order matches GameViewModel.GameMode
            val modeNames = stringArrayResource(R.array.game_modes)
            var modeIndex by rememberSaveable { mutableIntStateOf(0) }
            Dropdown(
                label = modeNames[modeIndex],
                items = modeNames.toList(),
                enabled = true,
                onSelected = { modeIndex = it },
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val running = gameStatus is GameViewModel.GameStatus.Running
                if (!running) {
                    Button(onClick = { GameViewModel.GameMode.entries.getOrNull(modeIndex)?.let(onStartGame) }) {
                        Text("Start Game")
                    }
                }
                Text(
                    gameStatusText(gameStatus),
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                )
                if (running) CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun gameStatusText(status: GameViewModel.GameStatus): String = stringResource(
    when (status) {
        GameViewModel.GameStatus.Waiting -> R.string.game_status_waiting
        GameViewModel.GameStatus.Over -> R.string.game_status_game_over
        is GameViewModel.GameStatus.Running -> when (status.mode) {
            GameViewModel.GameMode.NATT_DUELLEN -> R.string.game_status_nattduellen_started
            GameViewModel.GameMode.RANDOM_DRINK -> R.string.game_status_drink_started
        }
    }
)

/** A saved coaster; a long press starts dragging its address. The drag shadow is this row's icon and name. */
@Composable
private fun CoasterRow(coaster: CoasterItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .dragAndDropSource { _ ->
                DragAndDropTransferData(ClipData.newPlainText("coaster", coaster.address))
            }
            .padding(16.dp),
    ) {
        Icon(
            painterResource(R.drawable.coaster_icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            when (coaster.state) {
                CoasterConnection.State.READY -> "${coaster.name} · connected"
                CoasterConnection.State.CONNECTING -> "${coaster.name} · connecting…"
                else -> coaster.name
            },
            fontSize = 16.sp,
        )
    }
}

/**
 * A drop target for a coaster address, outlined while a drag hovers over it.
 * A placed coaster shows its battery: red when low, the percent greyed when stale.
 */
@Composable
private fun Circle(coasterId: String?, battery: BatteryView?, onDrop: (address: String) -> Unit, onLongPress: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnLongPress by rememberUpdatedState(onLongPress)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovered = true }
            override fun onExited(event: DragAndDropEvent) { hovered = false }
            override fun onEnded(event: DragAndDropEvent) { hovered = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val address = event.toAndroidDragEvent().clipData?.getItemAt(0)?.text?.toString()
                    ?: return false
                currentOnDrop(address)
                return true
            }
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(8.dp)
            .size(60.dp)
            .clip(CircleShape)
            .background(
                when {
                    coasterId == null -> EmptyCircle
                    battery?.status?.isLowBattery == true -> LowBattery
                    else -> AssignedCircle
                }
            )
            .then(if (coasterId != null || hovered) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
            .dragAndDropTarget(
                shouldStartDragAndDrop = { it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_PLAIN) },
                target = target,
            )
            .pointerInput(Unit) { detectTapGestures(onLongPress = { currentOnLongPress() }) },
    ) {
        if (coasterId != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(coasterId, color = Color.White, fontSize = 12.sp)
                if (battery != null) {
                    Text(
                        battery.status.circleText(),
                        color = if (battery.stale) Color.DarkGray else Color.White,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

/** Splits [count] circles into rows of at most four: 5 is 3+2, 7 is 4+3, 10 is 4+3+3. */
private fun circleRows(count: Int): List<Int> {
    val rows = mutableListOf<Int>()
    var remaining = count
    while (remaining > 0) {
        when {
            remaining == 5 -> { rows += listOf(3, 2); remaining = 0 }
            remaining == 7 -> { rows += listOf(4, 3); remaining = 0 }
            remaining % 4 == 0 -> { rows += 4; remaining -= 4 }
            remaining % 3 == 0 -> { rows += 3; remaining -= 3 }
            remaining > 4 -> { rows += 4; remaining -= 4 }
            else -> { rows += remaining; remaining = 0 }
        }
    }
    return rows
}

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun GameContentPreview() {
    CoasterTheme {
        GameContent(
            coasters = listOf(
                CoasterItem("F8:5B:1B:EB:1A:16", "Coaster-05", CoasterConnection.State.READY),
                CoasterItem("F8:5B:1B:EB:1A:2E", "Coaster-06", CoasterConnection.State.DISCONNECTED),
            ),
            circles = listOf("05", "06", null, null, null),
            batteries = mapOf(
                0 to BatteryView(BatteryStatus(3794, 82, ChargerState.CHARGING, 0x02, 0x02, 600, 0), stale = false),
                1 to BatteryView(BatteryStatus(3420, 4, ChargerState.LOW_BATTERY, 0x01, 0x03, 9000, 0), stale = false),
            ),
            gameStatus = GameViewModel.GameStatus.Waiting,
            onCircleCountSelected = {},
            onDrop = { _, _ -> },
            onUnassign = {},
            onStartGame = {},
        )
    }
}
