package com.example.myemptyapp

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
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
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myemptyapp.protocol.Pattern
import com.example.myemptyapp.protocol.Rgb
import com.flask.colorpicker.ColorPickerView
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private lateinit var spinner: Spinner
    private lateinit var savedDeviceNames: ArrayAdapter<String>
    private var isFirstSelection = true

    private lateinit var deviceAdapter: BluetoothDeviceAdapter

    private lateinit var outerCheckbox: CheckBox
    private lateinit var innerCheckbox: CheckBox

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
        savedDeviceNames = ArrayAdapter(this, R.layout.color_spinner_layout, mutableListOf(SELECT_PROMPT))
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
                val device = viewModel.savedDevices.value.getOrNull(position - 1) ?: return
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
            viewModel.setRings(outerCheckbox.isChecked, innerCheckbox.isChecked)
        }

        innerCheckbox.setOnCheckedChangeListener { _, _ ->
            viewModel.setRings(outerCheckbox.isChecked, innerCheckbox.isChecked)
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
                        viewModel.setPattern(Pattern.RAINBOW, Rgb(0, 255, 0))
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
        btnDisconnectDevice.setOnClickListener { viewModel.disconnect() }

        setControlsEnabled(viewModel.isReady.value)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.savedDevices.collect { devices ->
                        savedDeviceNames.clear()
                        savedDeviceNames.add(SELECT_PROMPT)
                        savedDeviceNames.addAll(devices.map { it.name })
                    }
                }
                launch { viewModel.scanResults.collect { deviceAdapter.submitList(it) } }
                launch { viewModel.isReady.collect { setControlsEnabled(it) } }
                launch { viewModel.messages.collect { showToast(it) } }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.stopScan()
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        if (!viewModel.isBluetoothEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            return
        }
        viewModel.startScan()
    }

    private fun connectTo(address: String, name: String) {
        withBluetoothPermissions { viewModel.connect(address, name) }
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
            button.isEnabled = viewModel.isReady.value
            viewModel.lastPickedColor?.let { button.setBackgroundColor(it) }
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
                viewModel.lastPickedColor = color

                // Get the selected mode from the spinner
                val selectedMode = (findViewById<Spinner>(R.id.dropdown_menu)).selectedItem.toString()

                // Send data to the BLE module
                viewModel.setPattern(Pattern.fromWireName(selectedMode) ?: Pattern.FIXED, Rgb.fromArgb(color))
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
        const val SELECT_PROMPT = "Select a device"
        val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
}
