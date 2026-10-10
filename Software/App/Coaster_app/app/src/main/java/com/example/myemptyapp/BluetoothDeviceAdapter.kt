package com.example.myemptyapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.myemptyapp.ble.ScannedCoaster

/** Scan results on the main screen. */
class BluetoothDeviceAdapter(
    private val onDeviceClicked: (ScannedCoaster) -> Unit
) : ListAdapter<ScannedCoaster, BluetoothDeviceAdapter.DeviceViewHolder>(DIFF) {

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
        val device = getItem(position)
        holder.nameTextView.text = device.name
        holder.addressTextView.text = device.address
        holder.itemView.setOnClickListener { onDeviceClicked(device) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<ScannedCoaster>() {
            override fun areItemsTheSame(oldItem: ScannedCoaster, newItem: ScannedCoaster) =
                oldItem.address == newItem.address

            override fun areContentsTheSame(oldItem: ScannedCoaster, newItem: ScannedCoaster) =
                oldItem == newItem
        }
    }
}
