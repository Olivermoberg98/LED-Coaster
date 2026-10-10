package com.example.myemptyapp

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myemptyapp.ble.CoasterConnection
import com.example.myemptyapp.protocol.Pattern
import com.example.myemptyapp.protocol.Rgb
import kotlinx.coroutines.launch

class GameActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper()) // For managing delayed tasks or callbacks
    private lateinit var recyclerViewDevices: RecyclerView

    private val viewModel: GameViewModel by viewModels()

    private lateinit var spinner: Spinner
    private lateinit var linearLayout: LinearLayout

    /** Circle views by position, rebuilt when the circle count changes. */
    private val circleViews = mutableListOf<View>()

    private lateinit var spinnerGameMode: Spinner
    private lateinit var buttonStartGame: Button

    private var currentGameRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        try {
            spinner = findViewById(R.id.spinnerCircleCount)
            linearLayout = findViewById(R.id.circleContainer)

            // Set up spinner options
            val circleCounts = arrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            val spinnerAdapter = ArrayAdapter(this, R.layout.color_spinner_layout, circleCounts)
            spinnerAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinner.adapter = spinnerAdapter

            spinner.setSelection(viewModel.circleCount.value - 1, false)

            // Listener for when the user selects an option
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parentView: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setCircleCount(circleCounts[position])
                }

                override fun onNothingSelected(parentView: AdapterView<*>?) {}
            }

            // Set up RecyclerView
            recyclerViewDevices = findViewById(R.id.recyclerViewDevices)
            // Every saved coaster; one dropped on a circle is connected there
            val coasterDevices = viewModel.coasters

            recyclerViewDevices.layoutManager = LinearLayoutManager(this)
            val devicesAdapter = DevicesAdapter(coasterDevices) { coasterDevice ->
                startDrag(coasterDevice)
            }
            recyclerViewDevices.adapter = devicesAdapter
            coasterDevices.forEachIndexed { index, coaster ->
                lifecycleScope.launch {
                    coaster.state.collect { devicesAdapter.notifyItemChanged(index) }
                }
            }

            // Game spinner
            spinnerGameMode = findViewById(R.id.spinnerGameMode)
            buttonStartGame = findViewById(R.id.buttonStartGame)

            val gameModes = resources.getStringArray(R.array.game_modes)
            val adapter = ArrayAdapter(this, R.layout.color_spinner_layout, gameModes)
            adapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
            spinnerGameMode.adapter = adapter

            // Handle button click
            buttonStartGame.setOnClickListener {
                val selectedMode = spinnerGameMode.selectedItem.toString()

                // Check if all circles are connected
                if (!viewModel.allCirclesReady()) {
                    Toast.makeText(this, "Please connect devices to all circles before starting!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Handle game mode
                when (selectedMode) {
                    gameModes[0] -> nattDuellen(viewModel.assignedCoasters.toMutableSet())
                    gameModes[1] -> drinkGame(viewModel.assignedCoasters.toMutableSet())
                    else -> {
                        Toast.makeText(this, "Invalid game mode selected!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error during onCreate initialization", e)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.circleCount.collect { updateCircleLayout() } }
                launch { viewModel.assignments.collect { bindCircles() } }
                launch {
                    viewModel.messages.collect {
                        Toast.makeText(this@GameActivity, it, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun startDrag(coasterDevice: CoasterConnection) {
        try {
            // Create a shadow for the drag action
            val view = recyclerViewDevices.findViewById<View>(R.id.deviceIcon)
            if (view.width > 0 && view.height > 0) {
                val shadow = View.DragShadowBuilder(view)

                // Pass the device object as local state
                recyclerViewDevices.startDragAndDrop(null, shadow, coasterDevice, 0)
                Log.d("GameActivity", "Drag started for ${coasterDevice.address}")
            } else {
                Log.e(
                    "GameActivity",
                    "View has invalid dimensions for drag: width = ${view.width}, height = ${view.height}"
                )
            }
        } catch (e: Exception) {
            Log.e("GameActivity", "Error starting drag", e)
        }
    }

    private fun createDragListener(circlePosition: Int): View.OnDragListener {
        return View.OnDragListener { v, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    val coasterDevice = event.localState as? CoasterConnection
                    if (coasterDevice != null) {
                        viewModel.assign(circlePosition, coasterDevice)
                    }
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    if (!event.result) v.setBackgroundResource(R.drawable.circle_background)
                    true
                }

                else -> false
            }
        }
    }

    // Dynamically add circles to the layout based on selected count
    private fun updateCircleLayout() {
        runOnUiThread {
            try {
                // Clear all existing views in the container
                linearLayout.removeAllViews()
                circleViews.clear()

                // Divide circles into rows
                val rows = mutableListOf<List<Int>>()
                var remainingCircles = viewModel.circleCount.value

                while (remainingCircles > 0) {
                    when {
                        remainingCircles == 5 -> {
                            rows.add(List(3) { 3 })
                            rows.add(List(2) { 2 })
                            remainingCircles = 0
                        }
                        remainingCircles == 7 -> {
                            rows.add(List(4) { 4 })
                            rows.add(List(3) { 3 })
                            remainingCircles = 0
                        }
                        remainingCircles % 4 == 0 -> {
                            rows.add(List(4) { 4 })
                            remainingCircles -= 4
                        }
                        remainingCircles % 3 == 0 -> {
                            rows.add(List(3) { 3 })
                            remainingCircles -= 3
                        }
                        remainingCircles > 4 -> {
                            rows.add(List(4) { 4 })
                            remainingCircles -= 4
                        }
                        else -> {
                            rows.add(List(remainingCircles) { remainingCircles })
                            remainingCircles = 0
                        }
                    }
                }

                // Track current circle position
                var circlePosition = 0

                // Create rows dynamically
                for (row in rows) {
                    val rowLayout = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(0, 16, 0, 16)
                        }
                        gravity = Gravity.CENTER
                    }

                    // Add circles to the row
                    for (i in 1..row[0]) {
                        val currentPosition = circlePosition
                        val circle = layoutInflater.inflate(R.layout.circle_layout, rowLayout, false)
                        circle.id = View.generateViewId()
                        rowLayout.addView(circle)

                        circleViews.add(circle)

                        // Set up drag listener for the circle with position
                        circle.setOnDragListener(createDragListener(currentPosition))

                        // Set up long click listener for the circle
                        circle.setOnLongClickListener {
                            viewModel.unassign(currentPosition)
                            true
                        }

                        circlePosition++
                    }
                    linearLayout.addView(rowLayout)
                }
                bindCircles()
            } catch (e: Exception) {
                Log.e("GameActivity", "Error updating circle layout", e)
            }
        }
    }

    /** Shows each circle's assigned coaster ID, or an empty circle. */
    private fun bindCircles() {
        val assignments = viewModel.assignments.value
        circleViews.forEachIndexed { position, circle ->
            val coaster = assignments[position]
            val ringTextView = circle.findViewById<TextView>(R.id.circleText)
            if (coaster != null) {
                ringTextView.text = coaster.coasterId
                ringTextView.visibility = View.VISIBLE
                circle.setBackgroundResource(R.drawable.circle_active_background)
            } else {
                ringTextView.visibility = View.GONE
                circle.setBackgroundResource(R.drawable.circle_background)
            }
        }
    }

    private fun nattDuellen(coasterDevices: MutableSet<CoasterConnection>) {
        // Cancel any previous game that might still be running
        cancelCurrentGame()

        Toast.makeText(this, "Starting Mode 1!", Toast.LENGTH_SHORT).show()
        val gameStatusText = findViewById<TextView>(R.id.gameStatusText)
        val gameProgressBar = findViewById<ProgressBar>(R.id.gameProgressBar)
        buttonStartGame.visibility = View.GONE
        gameStatusText.text = getString(R.string.game_status_nattduellen_started)
        gameProgressBar.visibility = View.VISIBLE

        // Light up all connected coasters with white
        for (coaster in coasterDevices) {
            coaster.sendPackage2(Pattern.FIXED, Rgb.WHITE)
        }

        val randomDelay = (5..10).random() * 1000L

        // Store the runnable so we can cancel it if needed
        currentGameRunnable = Runnable {
            val randomCoaster = coasterDevices.random()
            // Turn off by sending black color instead of disabling rings
            randomCoaster.sendPackage2(Pattern.FIXED, Rgb.OFF) // Black = off
            Log.d("Nattduellen", "Random coaster turned off: ${randomCoaster.name}")

            // Nested delayed task for cleanup
            handler.postDelayed({
                // Turn off ALL coasters with black color
                for (coaster in coasterDevices) {
                    coaster.sendPackage2(Pattern.FIXED, Rgb.OFF)
                }

                gameStatusText.text = getString(R.string.game_status_game_over)
                buttonStartGame.visibility = View.VISIBLE
                gameProgressBar.visibility = View.GONE

                Log.d("Nattduellen", "Game ended, all coasters reset")
            }, 3000)
        }

        handler.postDelayed(currentGameRunnable!!, randomDelay)
    }

    private fun drinkGame(coasterDevices: MutableSet<CoasterConnection>) {
        // Cancel any previous game that might still be running
        cancelCurrentGame()

        val gameDuration = (20..25).random() * 1000L
        val startTime = System.currentTimeMillis()
        val initialBounceInterval = 1600L // Start slow
        val finalBounceInterval = 200L // End fast
        var previousCoaster: CoasterConnection? = null // Track the last lit coaster

        buttonStartGame.visibility = View.GONE
        val gameStatusText = findViewById<TextView>(R.id.gameStatusText)
        val gameProgressBar = findViewById<ProgressBar>(R.id.gameProgressBar)
        gameStatusText.text = getString(R.string.game_status_drink_started)
        gameProgressBar.visibility = View.VISIBLE

        // Turn off all coasters at the start
        for (coaster in coasterDevices) {
            coaster.sendPackage2(Pattern.FIXED, Rgb.OFF)
        }

        fun lightUpAndTurnOff() {
            val elapsedTime = System.currentTimeMillis() - startTime

            if (elapsedTime > gameDuration) {
                // Game over - light up final coaster
                handler.postDelayed({
                    // Select a final coaster that's different from the previous one
                    val availableFinalCoasters = if (previousCoaster != null && coasterDevices.size > 1) {
                        coasterDevices.filter { it != previousCoaster }
                    } else {
                        coasterDevices.toList()
                    }

                    val finalCoaster = availableFinalCoasters.random()
                    val finalColor = generateRandomColor()

                    // Turn off ALL coasters
                    for (coaster in coasterDevices) {
                        coaster.sendPackage2(Pattern.FIXED, Rgb.OFF)
                    }

                    // Then light up the final one
                    handler.postDelayed({
                        finalCoaster.sendPackage2(Pattern.FIXED, finalColor)

                        Toast.makeText(this, "Game Over! ${finalCoaster.name} loses!", Toast.LENGTH_SHORT).show()
                        gameStatusText.text = getString(R.string.game_status_game_over)
                        buttonStartGame.visibility = View.VISIBLE
                        gameProgressBar.visibility = View.GONE
                    }, 100)
                }, 100)
                return
            }

            // Calculate current bounce interval based on elapsed time
            val progress = elapsedTime.toFloat() / gameDuration.toFloat()
            val currentBounceInterval = (initialBounceInterval - (initialBounceInterval - finalBounceInterval) * progress).toLong()

            // Select a random coaster that's different from the previous one
            val availableCoasters = if (previousCoaster != null && coasterDevices.size > 1) {
                coasterDevices.filter { it != previousCoaster }
            } else {
                coasterDevices.toList()
            }

            val randomCoaster = availableCoasters.random()
            val randomColor = generateRandomColor()

            // Turn off ALL coasters first
            for (coaster in coasterDevices) {
                if (coaster != randomCoaster) {
                    coaster.sendPackage2(Pattern.FIXED, Rgb.OFF)
                }
            }

            // Small delay, then light up the selected coaster
            handler.postDelayed({
                randomCoaster.sendPackage2(Pattern.FIXED, randomColor)
                previousCoaster = randomCoaster // Update the previous coaster

                // Schedule next bounce with the calculated interval
                currentGameRunnable = Runnable { lightUpAndTurnOff() }
                handler.postDelayed(currentGameRunnable!!, currentBounceInterval)
            }, 100)
        }
        // Wait a bit for the initial "off" commands to process
        handler.postDelayed({
            lightUpAndTurnOff()
        }, 100)
    }

    private fun cancelCurrentGame() {
        currentGameRunnable?.let {
            handler.removeCallbacks(it)
            Log.d("GameActivity", "Current game cancelled")
        }
        currentGameRunnable = null
    }

    private fun generateRandomColor(): Rgb =
        Rgb((0..255).random(), (0..255).random(), (0..255).random())

    override fun onDestroy() {
        super.onDestroy()
        cancelCurrentGame()
        handler.removeCallbacksAndMessages(null)
        Log.d("GameActivity", "All resources released")
    }
}
