package com.olivermoberg.ledcoaster

import android.app.Application
import com.olivermoberg.ledcoaster.ble.CoasterRepository
import com.olivermoberg.ledcoaster.ble.CoasterScanner
import com.olivermoberg.ledcoaster.data.SavedDevicesStore

class CoasterApp : Application() {
    val repository: CoasterRepository by lazy { CoasterRepository(this) }
    val scanner: CoasterScanner by lazy { CoasterScanner(this) }
    val savedDevices: SavedDevicesStore by lazy { SavedDevicesStore(this) }
}
