package com.example.myemptyapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException
import java.util.UUID

private const val PACKAGE_1_COMMAND: Byte = 0x01
private const val PACKAGE_2_COMMAND: Byte = 0x02

class GameActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper()) // For managing delayed tasks or callbacks
    private lateinit var recyclerViewDevices: RecyclerView

    private val ringDeviceMap = mutableMapOf<Int, CoasterDevice?>()
    private val assignedDevices = mutableSetOf<CoasterDevice>()

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothManager.adapter
    }

    private lateinit var spinner: Spinner
    private lateinit var linearLayout: LinearLayout
    private var selectedCircleCount = 0  // Default value

    private lateinit var spinnerGameMode: Spinner
    private lateinit var buttonStartGame: Button

    private var currentGameRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        try {
            spinner = findViewById(R.id.spinnerCircleCount)
            linearLayout = findViewById(R.id.circleContainer)

            // Set up spinner options
            val circleCounts = arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            val spinnerAdapter = ArrayAdapter(this, R.layout.color_spinner_layout, circleCounts)
            spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinner.adapter = spinnerAdapter

            // Listener for when the user selects an option
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parentView: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    try {
                        val newCircleCount = circleCounts[position]

                        // If reducing circle count, disconnect devices in circles beyond the new count
                        if (newCircleCount < selectedCircleCount) {
                            val devicesToRemove = mutableListOf<CoasterDevice>()

                            // Find devices in positions >= newCircleCount
                            for (pos in newCircleCount until selectedCircleCount) {
                                val device = ringDeviceMap[pos]
                                if (device != null) {
                                    devicesToRemove.add(device)
                                    ringDeviceMap.remove(pos)
                                }
                            }

                            // Disconnect and remove them
                            devicesToRemove.forEach { device ->
                                assignedDevices.remove(device)
                                device.disconnect()
                                Log.d("GameActivity", "Disconnected ${device.getDeviceName()} - circle removed")
                            }

                            if (devicesToRemove.isNotEmpty()) {
                                Toast.makeText(
                                    this@GameActivity,
                                    "${devicesToRemove.size} device(s) disconnected",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        selectedCircleCount = newCircleCount
                        updateCircleLayout()
                    } catch (e: Exception) {
                        Log.e("GameActivity", "Error updating circle layout", e)
                    }
                }

                override fun onNothingSelected(parentView: AdapterView<*>?) {}
            }

            // Set up RecyclerView
            recyclerViewDevices = findViewById(R.id.recyclerViewDevices)
            val deviceData = intent.getStringArrayListExtra("connectedDevices") ?: emptyList<String>()
            val coasterDevices = deviceData.mapNotNull { data ->
                try {
                    val parts = data.split("|")
                    if (parts.size == 2) {
                        val name = parts[0]
                        val address = parts[1]
                        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return@mapNotNull null

                        // CHECK IF DEVICE IS ACTUALLY CONNECTED
                        if (isDeviceConnected(device)) {
                            CoasterDevice(this, device) // Create CoasterDevice only if connected
                        } else {
                            Log.d("GameActivity", "Device $name is not currently connected, skipping")
                            null
                        }
                    } else null
                } catch (e: Exception) {
                    Log.e("GameActivity", "Invalid device data: $data", e)
                    null
                }
            }

            recyclerViewDevices.layoutManager = LinearLayoutManager(this)
            recyclerViewDevices.adapter = DevicesAdapter(coasterDevices) { coasterDevice ->
                startDrag(coasterDevice)
            }

            // Game spinner
            spinnerGameMode = findViewById(R.id.spinnerGameMode)
            buttonStartGame = findViewById(R.id.buttonStartGame)

            val gameModes = resources.getStringArray(R.array.game_modes)
            val adapter = ArrayAdapter(this, R.layout.color_spinner_layout, gameModes)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerGameMode.adapter = adapter

            // Handle button click
            buttonStartGame.setOnClickListener {
                val selectedMode = spinnerGameMode.selectedItem.toString()

                // Check if all circles are connected
                if (!areAllCirclesConnected()) {
                    Toast.makeText(this, "Please connect devices to all circles before starting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Handle game mode
                when (selectedMode) {
                    gameModes[0] -> nattDuellen(assignedDevices)
                    gameModes[1] -> drinkGame(assignedDevices)
                    else -> {
                        Toast.makeText(this, "Invalid game mode selected!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error during onCreate initialization", e)
        }
    }

    private fun startDrag(coasterDevice: CoasterDevice) {
        try {
            // Create a shadow for the drag action
            val view = recyclerViewDevices.findViewById<View>(R.id.deviceIcon)
            if (view.width > 0 && view.height > 0) {
                val shadow = View.DragShadowBuilder(view)

                // Pass the device object as local state
                recyclerViewDevices.startDragAndDrop(null, shadow, coasterDevice, 0)
                Log.d("GameActivity", "Drag started for ${coasterDevice.getDevice().address}")
            } else {
                Log.e(
                    "GameActivity",
                    "View has invalid dimensions for drag: width = ${view.width}, height = ${view.height}"
                )
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting drag", e)
        }
    }

    private fun createDragListener(circlePosition: Int): View.OnDragListener {
        return View.OnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    val coasterDevice = event.localState as? CoasterDevice
                    if (coasterDevice != null) {
                        // Check if the device is already assigned to a circle
                        if (assignedDevices.contains(coasterDevice)) {
                            Toast.makeText(
                                this,
                                "${coasterDevice.getDeviceName()} is already placed in another circle!",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@OnDragListener true
                        }

                        // Extract just the "00X" part of the device name
                        val deviceId = coasterDevice.getDeviceName().substringAfterLast('-')

                        // Find the TextView within the circle and set the device ID (e.g., "00X")
                        val ringTextView = (v as ViewGroup).findViewById<TextView>(R.id.circleText)
                        ringTextView.text = deviceId
                        ringTextView.visibility = View.VISIBLE

                        // Update UI and mappings using position instead of view ID
                        v.setBackgroundResource(R.drawable.circle_active_background)
                        ringDeviceMap[circlePosition] = coasterDevice
                        assignedDevices.add(coasterDevice)

                        // Connect to the device
                        coasterDevice.connect()
                    }
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    if (!event.result) v.setBackgroundResource(R.drawable.circle_background)
                    true
                }

                else -> false
            }
        }
    }

    // Dynamically add circles to the layout based on selected count
    fun updateCircleLayout() {
        runOnUiThread {
            try {
                // Clear all existing views in the container
                linearLayout.removeAllViews()

                // Divide circles into rows
                val rows = mutableListOf<List<Int>>()
                var remainingCircles = selectedCircleCount

                while (remainingCircles > 0) {
                    when {
                        remainingCircles == 5 -> {
                            rows.add(List(3) { 3 })
                            rows.add(List(2) { 2 })
                            remainingCircles = 0
                        }
                        remainingCircles == 7 -> {
                            rows.add(List(4) { 4 })
                            rows.add(List(3) { 3 })
                            remainingCircles = 0
                        }
                        remainingCircles % 4 == 0 -> {
                            rows.add(List(4) { 4 })
                            remainingCircles -= 4
                        }
                        remainingCircles % 3 == 0 -> {
                            rows.add(List(3) { 3 })
                            remainingCircles -= 3
                        }
                        remainingCircles > 4 -> {
                            rows.add(List(4) { 4 })
                            remainingCircles -= 4
                        }
                        else -> {
                            rows.add(List(remainingCircles) { remainingCircles })
                            remainingCircles = 0
                        }
                    }
                }

                // Track current circle position
                var circlePosition = 0

                // Create rows dynamically
                for (row in rows) {
                    val rowLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 16, 0, 16)
                        }
                        gravity = Gravity.CENTER
                    }

                    // Add circles to the row
                    for (i in 1..row[0]) {
                        val currentPosition = circlePosition
                        val circle = layoutInflater.inflate(R.layout.circle_layout, rowLayout, false)
                        circle.id = View.generateViewId()
                        rowLayout.addView(circle)

                        // Check if this position had a device assigned
                        val assignedDevice = ringDeviceMap[currentPosition]
                        if (assignedDevice != null) {
                            // Restore the device to this circle
                            val deviceId = assignedDevice.getDeviceName().substringAfterLast('-')
                            val ringTextView = circle.findViewById<TextView>(R.id.circleText)
                            ringTextView.text = deviceId
                            ringTextView.visibility = View.VISIBLE
                            circle.setBackgroundResource(R.drawable.circle_active_background)
                        }

                        // Set up drag listener for the circle with position
                        circle.setOnDragListener(createDragListener(currentPosition))

                        // Set up long click listener for the circle
                        circle.setOnLongClickListener {
                            val device = ringDeviceMap[currentPosition]
                            if (device != null) {
                                assignedDevices.remove(device)
                                ringDeviceMap.remove(currentPosition)
                                circle.setBackgroundResource(R.drawable.circle_background)
                                circle.findViewById<TextView>(R.id.circleText).visibility = View.GONE
                                Toast.makeText(
                                    this,
                                    "${device.getDeviceName()} removed",
                                    Toast.LENGTH_SHORT
                                ).show()
                                device.disconnect()
                            }
                            true
                        }

                        circlePosition++
                    }
                    linearLayout.addView(rowLayout)
                }
            } catch (e: Exception) {
                Log.e("GameActivity", "Error updating circle layout", e)
            }
        }
    }

    private fun areAllCirclesConnected(): Boolean {
        return assignedDevices.size == selectedCircleCount
    }

    private fun nattDuellen(coasterDevices: MutableSet<CoasterDevice>) {
        // Cancel any previous game that might still be running
        cancelCurrentGame()

        Toast.makeText(this, "Starting Mode 1!", Toast.LENGTH_SHORT).show()
        val gameStatusText = findViewById<TextView>(R.id.gameStatusText)
        val gameProgressBar = findViewById<ProgressBar>(R.id.gameProgressBar)
        buttonStartGame.visibility = View.GONE
        gameStatusText.text = getString(R.string.game_status_nattduellen_started)
        gameProgressBar.visibility = View.VISIBLE

        // Light up all connected coasters with white
        val whiteColorString = "255,255,255"

        for (coaster in coasterDevices) {
            coaster.sendPackage2("FIXED", whiteColorString)
        }

        val randomDelay = (5..10).random() * 1000L

        // Store the runnable so we can cancel it if needed
        currentGameRunnable = Runnable {
            val randomCoaster = coasterDevices.random()
            // Turn off by sending black color instead of disabling rings
            randomCoaster.sendPackage2("FIXED", "0,0,0") // Black = off
            Log.d("Nattduellen", "Random coaster turned off: ${randomCoaster.getDeviceName()}")

            // Nested delayed task for cleanup
            handler.postDelayed({
                // Turn off ALL coasters with black color
                for (coaster in coasterDevices) {
                    coaster.sendPackage2("FIXED", "0,0,0")
                }

                gameStatusText.text = getString(R.string.game_status_game_over)
                buttonStartGame.visibility = View.VISIBLE
                gameProgressBar.visibility = View.GONE

                Log.d("Nattduellen", "Game ended, all coasters reset")
            }, 3000)
        }

        handler.postDelayed(currentGameRunnable!!, randomDelay)
    }

    private fun drinkGame(coasterDevices: MutableSet<CoasterDevice>) {
        // Cancel any previous game that might still be running
        cancelCurrentGame()

        val gameDuration = (10..13).random() * 1000L // Random duration between 10 and 13 seconds
        val startTime = System.currentTimeMillis()

        buttonStartGame.visibility = View.GONE
        val gameStatusText = findViewById<TextView>(R.id.gameStatusText)
        val gameProgressBar = findViewById<ProgressBar>(R.id.gameProgressBar)
        gameStatusText.text = getString(R.string.game_status_drink_started)
        gameProgressBar.visibility = View.VISIBLE

        fun lightUpAndTurnOff() {
            if (System.currentTimeMillis() - startTime > gameDuration) {
                // Stop the game and light up one random coaster permanently
                val finalCoaster = coasterDevices.random()
                val finalColor = generateRandomColor()
                finalCoaster.sendPackage2("FIXED", finalColor)
                Toast.makeText(this, "Game Over! Final coaster lit up.", Toast.LENGTH_SHORT).show()

                // Turn off all other coasters with black color
                for (coaster in coasterDevices) {
                    if (coaster != finalCoaster) {
                        coaster.sendPackage2("FIXED", "0,0,0")
                    }
                }

                // Reset the UI after the game ends
                gameStatusText.text = getString(R.string.game_status_game_over)
                buttonStartGame.visibility = View.VISIBLE
                gameProgressBar.visibility = View.GONE
                return
            }

            val randomCoaster = coasterDevices.random()
            val randomColor = generateRandomColor()
            randomCoaster.sendPackage2("FIXED", randomColor)

            // Turn off the coaster after 0.5 seconds using black color
            handler.postDelayed({
                randomCoaster.sendPackage2("FIXED", "0,0,0")
                handler.postDelayed({ lightUpAndTurnOff() }, 0)
            }, 500)
        }

        lightUpAndTurnOff()
    }

    private fun cancelCurrentGame() {
        currentGameRunnable?.let {
            handler.removeCallbacks(it)
            Log.d("GameActivity", "Current game cancelled")
        }
        currentGameRunnable = null
    }

    private fun generateRandomColor(): String {
        val red = (0..255).random()
        val green = (0..255).random()
        val blue = (0..255).random()
        return "$red,$green,$blue"
    }

    private fun isDeviceConnected(device: BluetoothDevice): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                return false
            }

            // Check if device is in the list of connected devices
            val method = device.javaClass.getMethod("isConnected")
            method.invoke(device) as Boolean
        } catch (e: Exception) {
            Log.e("GameActivity", "Error checking device connection status", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelCurrentGame()
        // Clean up all connected devices
        ringDeviceMap.values.forEach { coasterDevice ->
            coasterDevice?.disconnect()
        }
        ringDeviceMap.clear()
        assignedDevices.clear()
        handler.removeCallbacksAndMessages(null)
        Log.d("GameActivity", "All resources released")
    }
}

class CoasterDevice(
    private val context: Context,
    private val device: BluetoothDevice
) {
    fun getDeviceName(): String = device.name ?: "Unknown Device"
    fun getDevice(): BluetoothDevice = device
    private var bluetoothGatt: BluetoothGatt? = null
    private var targetCharacteristic: BluetoothGattCharacteristic? = null
    private val REQUEST_BLUETOOTH_PERMISSIONS = 1
    private val MY_UUID = UUID.fromString("00001801-0000-1000-8000-008051234567")
    private var MY_CHAR_UUID = UUID.fromString("00001234-0000-1000-8000-001122334455")

    fun connect() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions(context)
            return
        }
        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                super.onConnectionStateChange(gatt, status, newState)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d("CoasterDevice", "Connected to ${device.address}")
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d("CoasterDevice", "Disconnected from ${device.address}")
                    }
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                super.onServicesDiscovered(gatt, status)
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // Assume the service and characteristic UUIDs are known
                    val service = gatt.getService(MY_UUID)
                    targetCharacteristic = service?.getCharacteristic(MY_CHAR_UUID)
                    Log.d("CoasterDevice", "Service and characteristic discovered for ${device.address}")
                }
            }
        })
    }

    fun sendPackage1(isOuterChecked: Boolean, isInnerChecked: Boolean) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions(context)
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

            targetCharacteristic?.let { characteristic ->
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeCharacteristic(characteristic, finalDataBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ?: BluetoothGatt.GATT_FAILURE
                    true
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = finalDataBytes
                    @Suppress("DEPRECATION")
                    bluetoothGatt?.writeCharacteristic(characteristic) ?: false
                }
                if (success) {
                    Log.d(ContentValues.TAG, "Data written to characteristic successfully")
                } else {
                    Log.e(ContentValues.TAG, "Failed to write data to characteristic")
                }
            } ?: Log.e(ContentValues.TAG, "Characteristic not initialized")
        } catch (e: IOException) {
            Log.e(ContentValues.TAG, "Error occurred during Bluetooth communication: ${e.message}", e)
        }
    }

    fun sendPackage2(mode: String, colors: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions(context)
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
            val finalDataBytes = dataBytes + checksum

            targetCharacteristic?.let { characteristic ->
                val success = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeCharacteristic(characteristic, finalDataBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) ?: BluetoothGatt.GATT_FAILURE
                    true
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = finalDataBytes
                    @Suppress("DEPRECATION")
                    bluetoothGatt?.writeCharacteristic(characteristic) ?: false
                }
                if (success) {
                    Log.d(ContentValues.TAG, "Data written to characteristic successfully")
                } else {
                    Log.e(ContentValues.TAG, "Failed to write data to characteristic")
                }
            } ?: Log.e(ContentValues.TAG, "Characteristic not initialized")
        } catch (e: IOException) {
            Log.e(ContentValues.TAG, "Error occurred during Bluetooth communication: ${e.message}", e)
        }
    }

    fun disconnect() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions(context)
            return
        }
        bluetoothGatt!!.disconnect()
        bluetoothGatt!!.close()
        bluetoothGatt = null
    }

    fun requestBluetoothPermissions(context: Context) {
        if (context is Activity) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.BLUETOOTH_CONNECT
                ),
                REQUEST_BLUETOOTH_PERMISSIONS
            )
        }
    }
}