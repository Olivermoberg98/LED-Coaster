package com.example.myemptyapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myemptyapp.ble.CoasterConnection
import com.example.myemptyapp.ble.ScannedCoaster
import com.example.myemptyapp.data.SavedDevice
import com.example.myemptyapp.protocol.Pattern
import com.example.myemptyapp.protocol.Rgb
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : AppCompatActivity() {

    private val app by lazy { application as CoasterApp }

    private lateinit var spinner: Spinner
    private lateinit var savedDeviceNames: ArrayAdapter<String>
    private val savedDevices = mutableListOf<SavedDevice>()
    private var isFirstSelection = true

    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private var scanJob: Job? = null

    private lateinit var outerCheckbox: CheckBox
    private lateinit var innerCheckbox: CheckBox

    /** The coaster this screen controls. */
    private var current: CoasterConnection? = null
    private var currentStateJob: Job? = null

    private var pendingPermissionAction: (() -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val action = pendingPermissionAction
            pendingPermissionAction = null
            if (results.values.all { it }) {
                action?.invoke()
            } else {
                showToast("Bluetooth permission is needed to find and control coasters")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val recyclerView: RecyclerView = findViewById(R.id.recyclerViewBluetoothDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        deviceAdapter = BluetoothDeviceAdapter { device -> connectTo(device.address, device.name) }
        recyclerView.adapter = deviceAdapter

        val btnConnectNewDevice: Button = findViewById(R.id.btnConnectNewDevice)
        btnConnectNewDevice.setOnClickListener {
            withBluetoothPermissions { startScan() }
        }

        // Spinner of previously connected coasters; position 0 is the prompt
        spinner = findViewById(R.id.spinnerPreviouslyConnectedDevices)
        savedDevices.addAll(app.savedDevices.all())
        savedDeviceNames = ArrayAdapter(
            this,
            R.layout.color_spinner_layout,
            (listOf(SELECT_PROMPT) + savedDevices.map { it.name }).toMutableList()
        )
        savedDeviceNames.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = savedDeviceNames

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (isFirstSelection) {
                    isFirstSelection = false
                    return  // Ignore the initial selection
                }
                if (position == 0) return
                val device = savedDevices[position - 1]
                connectTo(device.address, device.name)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinner.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick() // Ensure accessibility compliance
                spinner.setSelection(-1, false) // Reset the selection without notifying the listener
            }
            false // Allow other touch events to proceed
        }

        val gamesButton: Button = findViewById(R.id.gamesButton)
        gamesButton.setOnClickListener {
            withBluetoothPermissions { startActivity(Intent(this, GameActivity::class.java)) }
        }

        // Find the checkboxes by their IDs
        outerCheckbox = findViewById(R.id.text_outer)
        innerCheckbox = findViewById(R.id.text_inner)

        // Set listeners for each checkbox to handle their state changes
        outerCheckbox.setOnCheckedChangeListener { _, _ ->
            current?.sendPackage1(outerCheckbox.isChecked, innerCheckbox.isChecked)
        }

        innerCheckbox.setOnCheckedChangeListener { _, _ ->
            current?.sendPackage1(outerCheckbox.isChecked, innerCheckbox.isChecked)
        }

        // Spinner for different modes
        val dropdownItems = resources.getStringArray(R.array.dropdown_items)
        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, R.layout.color_spinner_layout, dropdownItems)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        val modeSpinner = findViewById<Spinner>(R.id.dropdown_menu)
        modeSpinner.adapter = adapter

        modeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                val selectedOption = parent.getItemAtPosition(position).toString()
                when (selectedOption) {
                    "FIXED" -> {
                        showColorPickerButton(1)
                    }

                    "PULSE" -> {
                        showColorPickerButton(1)
                    }

                    "CHASER" -> {
                        showColorPickerButton(1)
                    }

                    "RAINBOW" -> {
                        showColorPickerButton(0)
                        current?.sendPackage2(Pattern.RAINBOW, Rgb(0, 255, 0))
                    }

                    else -> {
                        showColorPickerButton(0)
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
            }
        }

        val btnDisconnectDevice: Button = findViewById(R.id.btnDisconnectDevice)
        btnDisconnectDevice.setOnClickListener {
            val connection = current
            if (connection == null || connection.state.value == CoasterConnection.State.DISCONNECTED) {
                showToast("No device is currently connected")
            } else {
                connection.disconnect()
            }
        }

        setControlsEnabled(false)
    }

    override fun onStop() {
        super.onStop()
        stopScan()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Leaving the app closes every coaster link; screens within it share them
        if (isFinishing) app.repository.disconnectAll()
    }

    private fun showToast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    /** Runs [action] once BLUETOOTH_SCAN and BLUETOOTH_CONNECT are granted, asking first if needed. */
    private fun withBluetoothPermissions(action: () -> Unit) {
        val missing = BLUETOOTH_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startScan() {
        if (scanJob?.isActive == true) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        if (!app.scanner.isBluetoothEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            return
        }
        val scan = app.scanner.scan()
        scanJob = lifecycleScope.launch {
            withTimeoutOrNull(SCAN_DURATION_MS) {
                scan
                    .catch { e ->
                        Log.w(TAG, "Scan ended with error", e)
                        showToast("Could not scan for coasters")
                    }
                    .collect { device -> onDeviceScanned(device) }
            }
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
    }

    private fun onDeviceScanned(device: ScannedCoaster) {
        if (!app.savedDevices.contains(device.address)) {
            deviceAdapter.addDevice(device)
        }
    }

    private fun connectTo(address: String, name: String) {
        withBluetoothPermissions {
            // An active scan slows connection setup
            stopScan()

            if (!app.savedDevices.contains(address)) {
                val device = SavedDevice(address, name)
                app.savedDevices.save(device)
                savedDevices.add(device)
                savedDeviceNames.add(name)
            }

            val connection = app.repository.connection(address, name)
            current = connection
            observe(connection)
            lifecycleScope.launch {
                if (!connection.connect() && current == connection) {
                    showToast("Could not connect to ${connection.name}")
                }
            }
        }
    }

    /** Follows [connection]'s state: unlocks the controls while ready and reports changes. */
    private fun observe(connection: CoasterConnection) {
        currentStateJob?.cancel()
        currentStateJob = lifecycleScope.launch {
            var wasReady = false
            connection.state.collect { state ->
                val ready = state == CoasterConnection.State.READY
                setControlsEnabled(ready)
                if (ready && !wasReady) showToast("Connected to ${connection.name}")
                if (!ready && wasReady) showToast("Disconnected from ${connection.name}")
                wasReady = ready
            }
        }
    }

    // Show color picker buttons based on selected option
    private fun showColorPickerButton(numButtonsToShow: Int) {
        val layout = findViewById<LinearLayout>(R.id.colorPickerButtonsLayout)
        layout.removeAllViews()

        for (i in 0 until numButtonsToShow) {
            val button = layoutInflater.inflate(R.layout.color_picker_button, layout, false) as View
            button.setOnClickListener {
                onChooseColorButtonClick(it)
                // Maybe add some if statement here to see if i >= 1 and then send a corresponding
                // color button index
            }
            button.isEnabled = current?.isReady == true
            layout.addView(button)
        }
    }

    // Handle button click to choose color
    private fun onChooseColorButtonClick(view: View) {
        ColorPickerDialogBuilder
            .with(this)
            .setTitle("Color 1")
            .initialColor(Color.RED)
            .wheelType(ColorPickerView.WHEEL_TYPE.CIRCLE)
            .density(12)
            .setOnColorSelectedListener { color -> // Handle the selected color here
                // For example, you can update UI elements with the selected color
                view.setBackgroundColor(color)

                // Get the selected mode from the spinner
                val selectedMode = (findViewById<Spinner>(R.id.dropdown_menu)).selectedItem.toString()

                // Send data to the BLE module
                current?.sendPackage2(Pattern.fromWireName(selectedMode) ?: Pattern.FIXED, Rgb.fromArgb(color))
            }
            .setPositiveButton("OK") { dialog, selectedColor, allColors ->
                // Handle OK button click if needed
            }
            .setNegativeButton("Cancel") { dialog, which ->
                // Handle Cancel button click if needed
            }
            .build()
            .show()
    }

    /** Locks the light and effect controls while no coaster is ready. Games stay reachable. */
    private fun setControlsEnabled(enabled: Boolean) {
        setViewAndChildrenEnabled(findViewById(R.id.layout_checkboxes), enabled)
        setViewAndChildrenEnabled(findViewById(R.id.ColorContainer), enabled)
    }

    private fun setViewAndChildrenEnabled(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                setViewAndChildrenEnabled(child, enabled)
            }
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val SELECT_PROMPT = "Select a device"
        const val SCAN_DURATION_MS = 10_000L
        val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
}
