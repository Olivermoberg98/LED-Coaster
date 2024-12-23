package com.example.myemptyapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.UUID

class GameActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper()) // For managing delayed tasks or callbacks
    private lateinit var recyclerViewDevices: RecyclerView

    private val ringDeviceMap = mutableMapOf<Int, CoasterDevice?>()
    private val assignedDevices = mutableSetOf<CoasterDevice>()

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private lateinit var spinner: Spinner
    private lateinit var linearLayout: LinearLayout
    private var selectedCircleCount = 2  // Default value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        try {
            spinner = findViewById(R.id.spinnerCircleCount)
            linearLayout = findViewById(R.id.circleContainer)

            // Set up spinner options
            val circleCounts = arrayOf(2, 3, 4, 5, 6, 7, 8, 9, 10)
            val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, circleCounts)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
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
                        resetAllCircles()
                        selectedCircleCount = circleCounts[position]
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
                        val device = bluetoothAdapter.getRemoteDevice(address)
                        CoasterDevice(this, device) // Create CoasterDevice directly
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

    private fun createDragListener(): View.OnDragListener {
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
                        ringTextView.text = deviceId // Only show the "00X" part
                        ringTextView.visibility = View.VISIBLE

                        // Update UI and mappings
                        v.setBackgroundResource(R.drawable.circle_active_background)
                        ringDeviceMap[v.id] = coasterDevice
                        assignedDevices.add(coasterDevice)

                        // Connect to the device
                        coasterDevice.connect()

                        // Use the dynamic ID for resource entry name
                        //val resourceName = resources.getResourceEntryName(v.id)
                        //Toast.makeText(this, "${coasterDevice.getDeviceName()} assigned to $resourceName", Toast.LENGTH_SHORT).show()
                    }
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    // Reset the circle background if the drag ended unsuccessfully
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
                            // Special case: 5 = 3 + 2
                            rows.add(List(3) { 3 })
                            rows.add(List(2) { 2 })
                            remainingCircles = 0
                        }

                        remainingCircles == 7 -> {
                            // Special case: 7 = 4 + 3
                            rows.add(List(4) { 4 })
                            rows.add(List(3) { 3 })
                            remainingCircles = 0
                        }

                        remainingCircles % 4 == 0 -> {
                            // Use rows of 4 when divisible by 4
                            rows.add(List(4) { 4 })
                            remainingCircles -= 4
                        }

                        remainingCircles % 3 == 0 -> {
                            // Use rows of 3 when divisible by 3
                            rows.add(List(3) { 3 })
                            remainingCircles -= 3
                        }

                        remainingCircles > 4 -> {
                            // If more than 4 but not divisible, prioritize rows of 4
                            rows.add(List(4) { 4 })
                            remainingCircles -= 4
                        }

                        else -> {
                            // Handle remaining circles (should only be 2 or 3 at this point)
                            rows.add(List(remainingCircles) { remainingCircles })
                            remainingCircles = 0
                        }
                    }
                }

                // Create rows dynamically
                for (row in rows) {
                    // Create a horizontal layout for the row
                    val rowLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 16, 0, 16) // Add spacing between rows
                        }
                        gravity = Gravity.CENTER // Center circles in the row
                    }

                    // Add circles to the row
                    for (i in 1..row[0]) {
                        val circle =
                            layoutInflater.inflate(R.layout.circle_layout, rowLayout, false)
                        circle.id = View.generateViewId()
                        rowLayout.addView(circle)

                        // Set up drag listener for the circle
                        circle.setOnDragListener(createDragListener())

                        // Set up long click listener for the circle
                        circle.setOnLongClickListener {
                            val device = ringDeviceMap[circle.id]
                            if (device != null) {
                                assignedDevices.remove(device)
                                ringDeviceMap[circle.id] = null
                                circle.setBackgroundResource(R.drawable.circle_background)
                                circle.findViewById<TextView>(R.id.circleText).visibility =
                                    View.GONE
                                Toast.makeText(
                                    this,
                                    "${device.getDeviceName()} removed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            device?.disconnect()
                            true
                        }
                    }
                    // Add the row layout to the parent linear layout
                    linearLayout.addView(rowLayout)
                }
            } catch (e: Exception) {
                Log.e("GameActivity", "Error updating circle layout", e)
            }
        }
    }

    private fun resetAllCircles() {
        runOnUiThread {
            try {
                linearLayout.removeAllViews()
            } catch (e: Exception) {
                Log.e("GameActivity", "Error resetting circles", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up all connected devices
        ringDeviceMap.values.forEach { coasterDevice ->
            coasterDevice?.disconnect()
        }
        ringDeviceMap.clear()
        assignedDevices.clear()
        handler.removeCallbacksAndMessages(null)
        Log.d("GameActivity", "All resources released")
    }

    override fun onStart() {
        super.onStart()
        Log.d("GameActivity", "onStart: Activity is starting.")
    }

    override fun onResume() {
        super.onResume()
        Log.d("GameActivity", "onResume: Activity is resuming.")
    }

    override fun onPause() {
        super.onPause()
        Log.d("GameActivity", "onPause: Activity is pausing.")
    }

    override fun onStop() {
        super.onStop()
        Log.d("GameActivity", "onStop: Activity is stopping.")
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
                    val service = gatt.getService(UUID.fromString("your-service-uuid"))
                    targetCharacteristic = service?.getCharacteristic(UUID.fromString("your-characteristic-uuid"))
                    Log.d("CoasterDevice", "Service and characteristic discovered for ${device.address}")
                }
            }
        })
    }

    fun sendData(data: ByteArray) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions(context)
            return
        }
        targetCharacteristic?.let {
            it.value = data
            bluetoothGatt?.writeCharacteristic(it)
            Log.d("CoasterDevice", "Data sent to ${device.address}: ${data.joinToString()}")
        } ?: Log.e("CoasterDevice", "Characteristic not initialized for ${device.address}")
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

data class Device(val name: String, val bluetoothDevice: BluetoothDevice)