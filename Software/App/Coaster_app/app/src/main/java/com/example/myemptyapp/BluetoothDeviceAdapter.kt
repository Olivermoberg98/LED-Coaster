package com.example.myemptyapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myemptyapp.ble.ScannedCoaster

/** Scan results on the main screen, kept sorted by name and unique by address. */
class BluetoothDeviceAdapter(
    private val onDeviceClicked: (ScannedCoaster) -> Unit
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    private val devices = mutableListOf<ScannedCoaster>()

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
        val device = devices[position]
        holder.nameTextView.text = device.name
        holder.addressTextView.text = device.address
        holder.itemView.setOnClickListener { onDeviceClicked(device) }
    }

    override fun getItemCount(): Int = devices.size

    /** Adds [device] in name order. A device already listed is ignored. */
    fun addDevice(device: ScannedCoaster) {
        if (devices.any { it.address == device.address }) return
        val index = devices.indexOfFirst { it.name > device.name }.let { if (it == -1) devices.size else it }
        devices.add(index, device)
        notifyItemInserted(index)
    }
}
