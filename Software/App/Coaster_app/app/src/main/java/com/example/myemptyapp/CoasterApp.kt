package com.example.myemptyapp

import android.app.Application
import com.example.myemptyapp.ble.CoasterRepository

class CoasterApp : Application() {
    val repository: CoasterRepository by lazy { CoasterRepository(this) }
}
