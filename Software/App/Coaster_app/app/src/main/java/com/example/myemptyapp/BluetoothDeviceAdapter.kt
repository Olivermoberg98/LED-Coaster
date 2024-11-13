import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.myemptyapp.R

class BluetoothDeviceAdapter(
    private val context: Context, // Application context
    public val deviceList: MutableList<BluetoothDevice>,
    private val listener: OnDeviceClickListener, // Listener for device clicks
    private val permissionCallback: () -> Unit // Callback to handle permission request
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    // Define the OnDeviceClickListener interface here
    interface OnDeviceClickListener {
        fun onDeviceClicked(device: BluetoothDevice)
    }

    // ViewHolder for each item in the RecyclerView
    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.text_device_name)
        val addressTextView: TextView = itemView.findViewById(R.id.text_device_address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = deviceList[position]

        // Check if permission is granted before accessing device name
        if (ContextCompat.checkSelfPermission(
                holder.itemView.context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val deviceName = device.name ?: "Unknown Device"
            holder.nameTextView.text = deviceName
        } else {
            // Permission not granted, request permission from the user
            permissionCallback.invoke()
        }

        holder.addressTextView.text = device.address

        // Set click listener for each item
        holder.itemView.setOnClickListener {
            listener.onDeviceClicked(device)
        }

        // Sort the deviceList by name after every onBindViewHolder call
        deviceList.sortBy { it.name }
    }

    override fun getItemCount(): Int {
        return deviceList.size
    }

    fun addDevice(device: BluetoothDevice) {
        // Check if permission is granted before accessing device name
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, proceed to add the device
            if (device.name != null) {
                // Check if the device address already exists in the list
                if (!deviceList.any { it.address == device.address }) {
                    deviceList.add(device)
                    notifyItemInserted(deviceList.size - 1)
                }
            }
        } else {
            // Permission not granted, request permission from the user
            permissionCallback.invoke()
        }
    }
}
