package com.example.myemptyapp

// Bluetooth imports
import BluetoothDeviceAdapter
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import java.io.OutputStream
import java.util.UUID
import java.io.IOException

// More general imports
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Spinner
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flask.colorpicker.ColorPickerView
//import com.flask.colorpicker.OnColorSelectedListener
import com.flask.colorpicker.builder.ColorPickerDialogBuilder
import kotlinx.coroutines.delay

// Define command bytes for each package
private const val PACKAGE_1_COMMAND: Byte = 0x01
private const val PACKAGE_2_COMMAND: Byte = 0x02

class MainActivity : AppCompatActivity(), BluetoothDeviceAdapter.OnDeviceClickListener {

    // Bluetooth variable initialization
    private val REQUEST_BLUETOOTH_PERMISSIONS = 1
    private val REQUEST_ENABLE_BT = 2
    private lateinit var selectedDevice: BluetoothDevice
    private lateinit var receiver: BroadcastReceiver
    private val MY_UUID = UUID.fromString("00001801-0000-1000-8000-008051234567")
    private var MY_CHAR_UUID = UUID.fromString("00001234-0000-1000-8000-001122334455")
    private var bluetoothSocket: BluetoothSocket? = null // Member variable to hold Bluetooth socket
    private lateinit var spinner: Spinner

    // RecyclerView and Adapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var deviceAdapter: BluetoothDeviceAdapter
    private lateinit var previousDeviceAdapter: BluetoothDeviceAdapter

    // Define a boolean flag to track the discovery state
    private var isDiscoveryInProgress = false
    private var isFirstSelection = true

    // Define buttons and spinners for sending data
    private lateinit var outerCheckbox: CheckBox
    private lateinit var innerCheckbox: CheckBox

    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // GATT connected, now you can interact with the GATT server
                Log.d(TAG, "GATT connected, discovering services...")
                gatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "GATT disconnected")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // Find the service and characteristic
                val service = gatt?.getService(MY_UUID)
                targetCharacteristic = service?.getCharacteristic(MY_CHAR_UUID)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Find the LinearLayout by its ID and initialize it
        //val layoutforCheckboxes = findViewById<LinearLayout>(R.id.unlockAfterBluetooth)
        //setViewAndChildrenEnabled(layoutforCheckboxes, false)

        // Initialize RecyclerView and its adapter
        recyclerView = findViewById(R.id.recyclerViewBluetoothDevices)
        recyclerView.layoutManager = LinearLayoutManager(this)
        val emptyDeviceList = mutableListOf<BluetoothDevice>()
        deviceAdapter = BluetoothDeviceAdapter(applicationContext,emptyDeviceList,this) {
            requestBluetoothPermissions()
        }
        recyclerView.adapter = deviceAdapter

        // Button click listener
        val btnConnectNewDevice: Button = findViewById(R.id.btnConnectNewDevice)
        btnConnectNewDevice.setOnClickListener {
            if (!isDiscoveryInProgress) {
                checkAndStartBluetoothOperations()
            }
        }

        // Register BroadcastReceiver
        registerBluetoothReceiver()

        // Delete saved items if you need
        //clearPreviouslyConnectedDevices()

        // Load previously connected devices
        val previouslyConnectedDevices = loadPreviouslyConnectedDevices().toMutableList()
        // Initialize the adapter with previously connected devices
        previousDeviceAdapter = BluetoothDeviceAdapter(applicationContext, previouslyConnectedDevices, this) {
            requestBluetoothPermissions()
        }

        // Spinner for bluetooth
        spinner = findViewById(R.id.spinnerPreviouslyConnectedDevices)
        val connectedDevices: MutableList<String> = mutableListOf("Select a device")
        connectedDevices.addAll(getPreviouslyConnectedDevices())
        val bluetoothadapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, R.layout.color_spinner_layout, connectedDevices)
        bluetoothadapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinner.adapter = bluetoothadapter

        // Set the onItemSelectedListener
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

                val selectedDeviceName = parent.getItemAtPosition(position).toString()
                if (selectedDeviceName == "Select a device") return

                // Check if the permission is granted
                if (ContextCompat.checkSelfPermission(
                        applicationContext,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    // Find the BluetoothDevice object by its name
                    val selectedDevice =
                        previousDeviceAdapter.deviceList.firstOrNull { it.name == selectedDeviceName }
                    selectedDevice?.let {
                        connectToDevice(it)
                    }
                } else {
                    requestBluetoothPermissions()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Handle the case where nothing is selected (optional)
            }
        }

        spinner.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick() // Ensure accessibility compliance
                spinner.setSelection(-1, false) // Reset the selection without notifying the listener
            }
            false // Allow other touch events to proceed
        }

        // Find the checkboxes by their IDs
        outerCheckbox = findViewById(R.id.text_outer)
        innerCheckbox = findViewById(R.id.text_inner)

        // Set listeners for each checkbox to handle their state changes
        outerCheckbox.setOnCheckedChangeListener { _, _ ->
            sendPackage1(outerCheckbox.isChecked, innerCheckbox.isChecked)
        }

        innerCheckbox.setOnCheckedChangeListener { _, _ ->
            sendPackage1(outerCheckbox.isChecked, innerCheckbox.isChecked)
        }

        // Spinner for different modes
        val dropdownItems = resources.getStringArray(R.array.dropdown_items)
        val adapter: ArrayAdapter<String> =
            ArrayAdapter<String>(this, R.layout.color_spinner_layout, dropdownItems)
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        val spinner = findViewById<Spinner>(R.id.dropdown_menu)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
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
                        showColorPickerButton(2)
                    }

                    "CHASER" -> {
                        showColorPickerButton(1)
                    }

                    "RAINBOW" -> {
                        val selectedMode = (findViewById<Spinner>(R.id.dropdown_menu)).selectedItem.toString()
                        showColorPickerButton(0)
                        sendPackage2(selectedMode, "0,255,0")
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
            disconnectFromDevice()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    override fun onDeviceClicked(device: BluetoothDevice) {
        connectToDevice(device)
    }

    // Get the previously connected devices
    private fun getPreviouslyConnectedDevices(): List<String> {
        val sharedPreferences = getSharedPreferences("BluetoothDevices", Context.MODE_PRIVATE)
        val deviceAddresses = sharedPreferences.all.keys.toList()
        val deviceNames = mutableListOf<String>()
        for (address in deviceAddresses) {
            // Fetch the device name for each device address
            val deviceName = sharedPreferences.getString(address, null)
            if (deviceName != null) {
                deviceNames.add(deviceName)
            }
        }
        return deviceNames
    }

    // Show error message
    private fun Context.showToast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    // Show color picker buttons based on selected option
    @RequiresApi(Build.VERSION_CODES.S)
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
            layout.addView(button)
        }
    }

    // Handle button click to choose color
    @RequiresApi(Build.VERSION_CODES.S)
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

                // Extract RGB components
                //val alpha = (color shr 24) and 0xFF
                val red = (color shr 16) and 0xFF
                val green = (color shr 8) and 0xFF
                val blue = color and 0xFF

                val colorString = "$red,$green,$blue"

                // Get the selected mode from the spinner
                val selectedMode = (findViewById<Spinner>(R.id.dropdown_menu)).selectedItem.toString()

                // Send data to the BLE module
                sendPackage2(selectedMode, colorString)
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

    @RequiresApi(Build.VERSION_CODES.S)
    // Package 1: Send two Boolean values
    private fun sendPackage1(isOuterChecked: Boolean, isInnerChecked: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions()
            return
        }
        try {
            val commandByte: Byte = PACKAGE_1_COMMAND
            val dataBytes = byteArrayOf(
                commandByte,
                if (isOuterChecked) 0x01 else 0x00,
                if (isInnerChecked) 0x01 else 0x00
            )

            // Calculate checksum by summing all bytes modulo 256
            val checksum: Byte = (dataBytes.sumOf { it.toInt() } % 256).toByte()

            // Append the checksum to the data array
            val finalDataBytes = dataBytes + checksum

            //bluetoothSocket?.outputStream?.write(finalDataBytes)
            //bluetoothSocket?.outputStream?.flush()
            // Get the characteristic (make sure you already discovered services)

            targetCharacteristic?.let { characteristic ->
                characteristic.value = finalDataBytes
                val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
                if (success) {
                    Log.d(TAG, "Data written to characteristic successfully")
                } else {
                    Log.e(TAG, "Failed to write data to characteristic")
                }
            } ?: Log.e(TAG, "Characteristic not initialized")
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred during Bluetooth communication: ${e.message}", e)
            showToast("Error: Failed to communicate with Bluetooth device")
        }
    }


    // Package 2: Send mode and list of colors
    @RequiresApi(Build.VERSION_CODES.S)
    private fun sendPackage2(mode: String, colors: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions()
            return
        }
        try {
            val commandByte: Byte = PACKAGE_2_COMMAND
            val modeBytes = mode.toByteArray()
            val colorBytes = colors.toByteArray()
            val dataBytes = byteArrayOf(commandByte) + modeBytes + byteArrayOf(0x2C) + colorBytes

            // Calculate checksum by summing all bytes modulo 256
            val checksum: Byte = (dataBytes.sumOf { it.toInt() } % 256).toByte()

            // Append the checksum to the data array
            val finalDataBytes = dataBytes + checksum.toByte()

            targetCharacteristic?.let { characteristic ->
                characteristic.value = finalDataBytes
                val success = bluetoothGatt?.writeCharacteristic(characteristic) ?: false
                if (success) {
                    Log.d(TAG, "Data written to characteristic successfully")
                } else {
                    Log.e(TAG, "Failed to write data to characteristic")
                }
            } ?: Log.e(TAG, "Characteristic not initialized")
        } catch (e: IOException) {
            Log.e(TAG, "Error occurred during Bluetooth communication: ${e.message}", e)
            showToast("Error: Failed to communicate with Bluetooth device")
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_CONNECT,
                ),
            REQUEST_BLUETOOTH_PERMISSIONS
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkAndStartBluetoothOperations() {
        // Check if Bluetooth permissions are granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADMIN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermissions()
        } else {
            // Check if Bluetooth is available and enabled
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                // Bluetooth is not available or not enabled, prompt user to enable it using Intent
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT, null)
            } else {
                // Bluetooth is enabled, start device discovery
                isDiscoveryInProgress = false
                bluetoothAdapter.startDiscovery()
            }
        }
    }

    private fun registerBluetoothReceiver() {
        // Register a BroadcastReceiver to receive device discovery results
        receiver = object : BroadcastReceiver() {
            @RequiresApi(Build.VERSION_CODES.S)
            override fun onReceive(context: Context?, intent: Intent?) {
                val action: String? = intent?.action
                if (BluetoothDevice.ACTION_FOUND == action) {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    // Add the device to your list or RecyclerView to display it in your UI
                    device?.let {
                        selectedDevice = it // Initialize selectedDevice
                        if (!isPreviouslyConnected(it)) {
                            deviceAdapter.addDevice(selectedDevice)
                        }
                    }
                } else if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                    // Connection successful, show toast message indicating successful connection
                    val device: BluetoothDevice? = intent?.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        showToast("Successfully connected")
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        registerReceiver(receiver, filter)
    }

    // Unregister the BroadcastReceiver when the activity stops or destroys
    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun connectToDevice(device: BluetoothDevice) {
        // Check if Bluetooth permissions are granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions()
            return
        }
        // Permissions are already granted, proceed with Bluetooth operations
        var attempts = 0
        val maxAttempts = 3 // Maximum number of connection attempts

        while (attempts < maxAttempts) {
            try {
                // Initiate pairing process if not already paired
                if (device.bondState != BluetoothDevice.BOND_BONDED) {
                    device.createBond()
                }

                bluetoothGatt = device.connectGatt(this, false, gattCallback)

                // Create a Bluetooth socket and connect to the selected device
                //bluetoothSocket = device.createRfcommSocketToServiceRecord(MY_UUID)
                //bluetoothSocket?.connect()

                // Connection successful, enable UI elements for sending data
                enableSendDataUI()

                // Check if the device is new or previously connected
                if (!isPreviouslyConnected(device)) {
                    // Save the bluetooth module name if it's a new device
                    val sharedPreferences =
                        getSharedPreferences("BluetoothDevices", Context.MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.putString(device.address, device.name)
                    editor.apply()

                    // Update the Spinner with the newly connected device name
                    runOnUiThread {
                        (spinner.adapter as? ArrayAdapter<String>)?.apply {
                            add(device.name)
                            notifyDataSetChanged()
                        }
                    }
                }

                // Now you can send information through the socket
                //val outputStream: OutputStream? = bluetoothSocket?.outputStream
                break
            } catch (e: IOException) {
                attempts++
                Log.e(TAG, "Error occurred during Bluetooth communication: ${e.message}", e)
                showToast("Error: Failed to connect to Bluetooth device. Attempt $attempts/$maxAttempts")
            }
        }
        // All attempts failed
        if (attempts >= maxAttempts) {
            showToast("Error: Failed to connect to Bluetooth device after $maxAttempts attempts.")
            // Optionally disable UI elements related to sending data here
        }
    }

    // Function to enable UI elements for sending data
    private fun enableSendDataUI() {
        val layoutforCheckboxes = findViewById<LinearLayout>(R.id.unlockAfterBluetooth)
        setViewAndChildrenEnabled(layoutforCheckboxes, true)
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

    private fun isPreviouslyConnected(device: BluetoothDevice): Boolean {
        val sharedPreferences = getSharedPreferences("BluetoothDevices", Context.MODE_PRIVATE)
        return sharedPreferences.contains(device.address)
    }

    private fun clearPreviouslyConnectedDevices() {
        val sharedPreferences = getSharedPreferences("BluetoothDevices", Context.MODE_PRIVATE)
        sharedPreferences.edit().clear().apply()
    }

    private fun loadPreviouslyConnectedDevices(): List<BluetoothDevice> {
        val sharedPreferences = getSharedPreferences("BluetoothDevices", Context.MODE_PRIVATE)
        val deviceAddresses = sharedPreferences.all.keys
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val deviceList = mutableListOf<BluetoothDevice>()

        for (address in deviceAddresses) {
            val device = bluetoothAdapter.getRemoteDevice(address)
            deviceList.add(device)
        }
        return deviceList
    }

    // Function to disconnect the current device
    private fun disconnectFromDevice() {
        if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
            try {
                bluetoothSocket!!.close()
                bluetoothSocket = null
                Toast.makeText(this, "Disconnected from device", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(this, "Failed to disconnect: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(this, "No device is currently connected", Toast.LENGTH_SHORT).show()
        }
    }
}