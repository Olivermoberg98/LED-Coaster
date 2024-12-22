package com.example.myemptyapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DevicesAdapter(
    private val devices: List<CoasterDevice>, // Change type to CoasterDevice
    private val onItemLongPress: (CoasterDevice) -> Unit // Update the callback type
) : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val coasterDevice = devices[position] // Use CoasterDevice
        holder.deviceName.text = coasterDevice.getDeviceName()
        holder.deviceIcon.setImageResource(R.drawable.coaster_icon)

        // Set up long press listener to start drag
        holder.itemView.setOnLongClickListener {
            onItemLongPress(coasterDevice) // Pass CoasterDevice to the callback
            true
        }
    }

    override fun getItemCount() = devices.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceIcon: ImageView = itemView.findViewById(R.id.deviceIcon)
    }
}
