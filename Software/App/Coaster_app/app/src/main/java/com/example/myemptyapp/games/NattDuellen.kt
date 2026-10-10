package com.example.myemptyapp.games

import com.example.myemptyapp.protocol.Rgb
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Every coaster lights white. After 5–10 s one random coaster goes dark, and
 * 3 s later all of them do.
 */
class NattDuellen(private val random: Random = Random.Default) : Game {

    override suspend fun play(coasters: List<CoasterController>): CoasterController? {
        require(coasters.isNotEmpty()) { "No coasters to play with" }

        coasters.showAll(Rgb.WHITE)
        delay(random.nextInt(MIN_WAIT_S, MAX_WAIT_S + 1) * 1000L)
        coasters.random(random).showColor(Rgb.OFF)
        delay(END_DELAY_MS)
        coasters.showAll(Rgb.OFF)
        return null
    }

    companion object {
        const val MIN_WAIT_S = 5
        const val MAX_WAIT_S = 10
        const val END_DELAY_MS = 3000L
    }
}
