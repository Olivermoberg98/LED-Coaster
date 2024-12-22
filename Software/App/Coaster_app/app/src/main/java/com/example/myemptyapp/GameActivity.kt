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
import android.util.Log
import android.view.DragEvent
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

    private lateinit var recyclerViewDevices: RecyclerView
    private lateinit var circle1: View
    private lateinit var circle2: View
    private lateinit var circle3: View

    private val ringDeviceMap = mutableMapOf<Int, CoasterDevice?>()
    private val assignedDevices = mutableSetOf<CoasterDevice>()

    private val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    private lateinit var spinner: Spinner
    private lateinit var linearLayout: LinearLayout
    private var selectedCircleCount = 3  // Default value

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        spinner = findViewById(R.id.spinnerCircleCount)
        linearLayout = findViewById(R.id.circleContainer)

        // Set up spinner options
        val circleCounts = arrayOf(2, 3, 4, 5, 6, 7, 8, 9, 10)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, circleCounts)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        // Listener for when the user selects an option
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parentView: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCircleCount = circleCounts[position]
                updateCircleLayout()
            }
            override fun onNothingSelected(parentView: AdapterView<*>?) {}
        }
        updateCircleLayout()

        recyclerViewDevices = findViewById(R.id.recyclerViewDevices)
        circle1 = findViewById(R.id.circle1)
        circle2 = findViewById(R.id.circle2)
        circle3 = findViewById(R.id.circle3)

        // Get the device data from the Intent
        val deviceData = intent.getStringArrayListExtra("connectedDevices") ?: emptyList<String>()
        val coasterDevices = deviceData.mapNotNull { data ->
            val parts = data.split("|")
            if (parts.size == 2) {
                val name = parts[0]
                val address = parts[1]
                val device = bluetoothAdapter.getRemoteDevice(address)
                CoasterDevice(this, device) // Create CoasterDevice directly
            } else null
        }

        // Set up RecyclerView
        recyclerViewDevices.layoutManager = LinearLayoutManager(this)
        recyclerViewDevices.adapter = DevicesAdapter(coasterDevices) { coasterDevice ->
            startDrag(coasterDevice)
        }

        // Set drag listeners on circles
        val dragListener = createDragListener()
        circle1.setOnDragListener(dragListener)
        circle2.setOnDragListener(dragListener)
        circle3.setOnDragListener(dragListener)

        circle1.setOnLongClickListener {
            val device = ringDeviceMap[circle1.id]
            if (device != null) {
                assignedDevices.remove(device)
                ringDeviceMap[circle1.id] = null
                circle1.setBackgroundResource(R.drawable.circle_background)
                circle1.findViewById<TextView>(R.id.circleText).visibility = View.GONE
                Toast.makeText(this, "${device.getDeviceName()} removed from circle1", Toast.LENGTH_SHORT).show()
            }
            device?.disconnect()
            true
        }
        circle2.setOnLongClickListener {
            val device = ringDeviceMap[circle2.id]
            if (device != null) {
                assignedDevices.remove(device)
                ringDeviceMap[circle2.id] = null
                circle2.setBackgroundResource(R.drawable.circle_background)
                circle2.findViewById<TextView>(R.id.circleText).visibility = View.GONE
                Toast.makeText(this, "${device.getDeviceName()} removed from circle2", Toast.LENGTH_SHORT).show()
            }
            device?.disconnect()
            true
        }
        circle3.setOnLongClickListener {
            val device = ringDeviceMap[circle3.id]
            if (device != null) {
                assignedDevices.remove(device)
                ringDeviceMap[circle3.id] = null
                circle3.setBackgroundResource(R.drawable.circle_background)
                circle3.findViewById<TextView>(R.id.circleText).visibility = View.GONE
                Toast.makeText(this, "${device.getDeviceName()} removed from circle3", Toast.LENGTH_SHORT).show()
            }
            device?.disconnect()
            true
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
                Log.e("GameActivity", "View has invalid dimensions for drag: width = ${view.width}, height = ${view.height}")
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
                            Toast.makeText(this, "${coasterDevice.getDeviceName()} is already placed in another circle!", Toast.LENGTH_SHORT).show()
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

                        Toast.makeText(this, "${coasterDevice.getDeviceName()} assigned to ${resources.getResourceEntryName(v.id)}", Toast.LENGTH_SHORT).show()
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

    // Dynamically add circles to the layout based on selected count
    private fun updateCircleLayout() {
        linearLayout.removeAllViews() // Remove all existing circles

        var rowLayout: LinearLayout? = null
        for (i in 1..selectedCircleCount) {
            // Create a new row when needed
            if (i % 4 == 1) {
                rowLayout = LinearLayout(this)
                rowLayout.orientation = LinearLayout.HORIZONTAL
                linearLayout.addView(rowLayout)
            }

            // Add circle to the row
            val circle = LayoutInflater.from(this).inflate(R.layout.circle_layout, null) // circle_layout is the circle item layout
            rowLayout?.addView(circle)
        }
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