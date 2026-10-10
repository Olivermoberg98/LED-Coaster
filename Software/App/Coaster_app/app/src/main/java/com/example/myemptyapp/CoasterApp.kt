package com.example.myemptyapp

import android.app.Application
import com.example.myemptyapp.ble.CoasterRepository
import com.example.myemptyapp.ble.CoasterScanner
import com.example.myemptyapp.data.SavedDevicesStore

class CoasterApp : Application() {
    val repository: CoasterRepository by lazy { CoasterRepository(this) }
    val scanner: CoasterScanner by lazy { CoasterScanner(this) }
    val savedDevices: SavedDevicesStore by lazy { SavedDevicesStore(this) }
}
