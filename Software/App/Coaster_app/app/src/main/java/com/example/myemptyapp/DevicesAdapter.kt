package com.example.myemptyapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myemptyapp.ble.CoasterConnection

class DevicesAdapter(
    private val devices: List<CoasterConnection>,
    private val onItemLongPress: (CoasterConnection) -> Unit
) : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val coasterDevice = devices[position]
        holder.deviceName.text = when (coasterDevice.state.value) {
            CoasterConnection.State.READY -> "${coasterDevice.name} · connected"
            CoasterConnection.State.CONNECTING -> "${coasterDevice.name} · connecting…"
            else -> coasterDevice.name
        }
        holder.deviceIcon.setImageResource(R.drawable.coaster_icon)

        // Set up long press listener to start drag
        holder.itemView.setOnLongClickListener {
            onItemLongPress(coasterDevice)
            true
        }
    }

    override fun getItemCount() = devices.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val deviceName: TextView = itemView.findViewById(R.id.deviceName)
        val deviceIcon: ImageView = itemView.findViewById(R.id.deviceIcon)
    }
}
