package com.olivermoberg.ledcoaster.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.olivermoberg.ledcoaster.R
import com.olivermoberg.ledcoaster.ble.ScannedCoaster
import com.olivermoberg.ledcoaster.data.SavedDevice
import com.olivermoberg.ledcoaster.protocol.Pattern
import com.olivermoberg.ledcoaster.ui.CardBackground
import com.olivermoberg.ledcoaster.ui.CoasterTheme
import com.olivermoberg.ledcoaster.ui.ScreenBackground

/** The single-coaster control screen, wired to [viewModel]. Actions that need the activity go out as callbacks. */
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNewDevice: () -> Unit,
    onConnect: (address: String, name: String) -> Unit,
    onChooseColor: () -> Unit,
    onGames: () -> Unit,
) {
    val savedDevices by viewModel.savedDevices.collectAsStateWithLifecycle()
    val currentName by viewModel.currentName.collectAsStateWithLifecycle()
    val scanResults by viewModel.scanResults.collectAsStateWithLifecycle()
    val isReady by viewModel.isReady.collectAsStateWithLifecycle()
    val outerEnabled by viewModel.outerEnabled.collectAsStateWithLifecycle()
    val innerEnabled by viewModel.innerEnabled.collectAsStateWithLifecycle()
    val pattern by viewModel.pattern.collectAsStateWithLifecycle()
    val pickedColor by viewModel.pickedColor.collectAsStateWithLifecycle()

    MainContent(
        savedDevices = savedDevices,
        currentName = currentName,
        scanResults = scanResults,
        isReady = isReady,
        outerEnabled = outerEnabled,
        innerEnabled = innerEnabled,
        pattern = pattern,
        pickedColor = pickedColor?.let { Color(it) },
        onNewDevice = onNewDevice,
        onConnect = onConnect,
        onDisconnect = viewModel::disconnect,
        onOuterChange = viewModel::setOuterEnabled,
        onInnerChange = viewModel::setInnerEnabled,
        onPatternSelected = viewModel::selectPattern,
        onChooseColor = onChooseColor,
        onGames = onGames,
    )
}

/**
 * The screen as plain state. The light and effect controls are locked until
 * [isReady]; the games button never is.
 */
@Composable
private fun MainContent(
    savedDevices: List<SavedDevice>,
    currentName: String?,
    scanResults: List<ScannedCoaster>,
    isReady: Boolean,
    outerEnabled: Boolean,
    innerEnabled: Boolean,
    pattern: Pattern,
    pickedColor: Color?,
    onNewDevice: () -> Unit,
    onConnect: (address: String, name: String) -> Unit,
    onDisconnect: () -> Unit,
    onOuterChange: (Boolean) -> Unit,
    onInnerChange: (Boolean) -> Unit,
    onPatternSelected: (Pattern) -> Unit,
    onChooseColor: () -> Unit,
    onGames: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBackground)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        Section(stringResource(R.string.bluetooth)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Dropdown(
                        label = currentName ?: "Select a device",
                        items = savedDevices.map { it.name },
                        enabled = savedDevices.isNotEmpty(),
                        onSelected = { index ->
                            val device = savedDevices[index]
                            onConnect(device.address, device.name)
                        },
                    )
                    Button(onClick = onNewDevice, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("New device")
                    }
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Disconnect")
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    scanResults.forEach { device ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConnect(device.address, device.name) }
                                .padding(10.dp),
                        ) {
                            Text(device.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(device.address, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Section(stringResource(R.string.light_configuration)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingCheckbox(stringResource(R.string.checkbox_outer), outerEnabled, isReady, onOuterChange)
                RingCheckbox(stringResource(R.string.checkbox_inner), innerEnabled, isReady, onInnerChange)
            }
        }

        Section(stringResource(R.string.effects)) {
            // Wire names, kept in arrays.xml so a test can hold them to the Pattern enum
            val patternNames = stringArrayResource(R.array.dropdown_items).toList()
            Dropdown(
                label = pattern.wireName,
                items = patternNames,
                enabled = isReady,
                onSelected = { index -> Pattern.fromWireName(patternNames[index])?.let(onPatternSelected) },
            )
            if (pattern != Pattern.RAINBOW) {
                val buttonColor = pickedColor ?: MaterialTheme.colorScheme.primary
                Button(
                    onClick = onChooseColor,
                    enabled = isReady,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = buttonColor,
                        contentColor = if (buttonColor.luminance() > 0.5f) Color.Black else Color.White,
                    ),
                    modifier = Modifier.padding(top = 16.dp).width(160.dp),
                ) {
                    Text("Choose Color")
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TextButton(onClick = onGames, modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Text("Go to Games", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
internal fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            content()
        }
    }
}

@Composable
private fun RingCheckbox(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 24.dp)) {
        Checkbox(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = CheckboxDefaults.colors(uncheckedColor = Color.White),
        )
        Text(label, fontSize = 18.sp, color = if (enabled) Color.White else Color.Gray)
    }
}

/** A text field look-alike that opens a menu of [items]. */
@Composable
internal fun Dropdown(label: String, items: List<String>, enabled: Boolean, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text("▾", fontSize = 18.sp)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    text = { Text(item, fontSize = 18.sp) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                )
            }
        }
    }
}

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun MainContentPreview() {
    CoasterTheme {
        MainContent(
            savedDevices = listOf(SavedDevice("F8:5B:1B:EB:1A:16", "Coaster-05")),
            currentName = "Coaster-05",
            scanResults = listOf(ScannedCoaster("F8:5B:1B:EB:1A:2E", "Coaster-06")),
            isReady = true,
            outerEnabled = true,
            innerEnabled = false,
            pattern = Pattern.PULSE,
            pickedColor = Color(0xFF2196F3),
            onNewDevice = {},
            onConnect = { _, _ -> },
            onDisconnect = {},
            onOuterChange = {},
            onInnerChange = {},
            onPatternSelected = {},
            onChooseColor = {},
            onGames = {},
        )
    }
}
