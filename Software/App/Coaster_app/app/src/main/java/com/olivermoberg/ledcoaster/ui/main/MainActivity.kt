package com.olivermoberg.ledcoaster.ui.main

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.olivermoberg.ledcoaster.ui.CoasterTheme
import com.olivermoberg.ledcoaster.ui.game.GameActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private var pendingPermissionAction: (() -> Unit)? = null
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val action = pendingPermissionAction
            pendingPermissionAction = null
            if (results.values.all { it }) {
                action?.invoke()
            } else {
                showToast("Bluetooth permission is needed to find and control coasters")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoasterTheme {
                MainScreen(
                    viewModel = viewModel,
                    onNewDevice = { withBluetoothPermissions { startScan() } },
                    onConnect = ::connectTo,
                    onGames = {
                        withBluetoothPermissions { startActivity(Intent(this, GameActivity::class.java)) }
                    },
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.messages.collect { showToast(it) }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) viewModel.stopScan()
    }

    private fun showToast(message: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this, message, duration).show()
    }

    /** Runs [action] once BLUETOOTH_SCAN and BLUETOOTH_CONNECT are granted, asking first if needed. */
    private fun withBluetoothPermissions(action: () -> Unit) {
        val missing = BLUETOOTH_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingPermissionAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startScan() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) return
        if (!viewModel.isBluetoothEnabled) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            return
        }
        viewModel.startScan()
    }

    private fun connectTo(address: String, name: String) {
        withBluetoothPermissions { viewModel.connect(address, name) }
    }

    private companion object {
        val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
}
