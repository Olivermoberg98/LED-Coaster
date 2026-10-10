package com.olivermoberg.ledcoaster.games

import com.olivermoberg.ledcoaster.protocol.Rgb
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * A random colour bounces between coasters, never landing on the same one
 * twice in a row, speeding up from 1600 ms to 200 ms per bounce over 20–25 s.
 * The coaster it finally stops on loses.
 */
class RandomDrink(
    private val random: Random = Random.Default,
    private val timeSource: TimeSource = TimeSource.Monotonic
) : Game {

    override suspend fun play(coasters: List<CoasterController>): CoasterController {
        require(coasters.isNotEmpty()) { "No coasters to play with" }

        val durationMs = random.nextInt(MIN_DURATION_S, MAX_DURATION_S + 1) * 1000L
        coasters.showAll(Rgb.OFF)

        val start = timeSource.markNow()
        var previous: CoasterController? = null
        while (true) {
            val elapsedMs = start.elapsedNow().inWholeMilliseconds
            if (elapsedMs > durationMs) break

            val progress = elapsedMs.toFloat() / durationMs
            val intervalMs = (INITIAL_INTERVAL_MS - (INITIAL_INTERVAL_MS - FINAL_INTERVAL_MS) * progress).toLong()

            val next = pickOther(coasters, previous)
            coasters.filter { it != next }.showAll(Rgb.OFF)
            next.showColor(random.nextColor())
            previous = next
            delay(intervalMs)
        }

        val loser = pickOther(coasters, previous)
        coasters.showAll(Rgb.OFF)
        loser.showColor(random.nextColor())
        return loser
    }

    /** A random coaster other than [previous], unless there is only one. */
    private fun pickOther(coasters: List<CoasterController>, previous: CoasterController?): CoasterController {
        val candidates = if (previous != null && coasters.size > 1) coasters - previous else coasters
        return candidates.random(random)
    }

    companion object {
        const val MIN_DURATION_S = 20
        const val MAX_DURATION_S = 25
        const val INITIAL_INTERVAL_MS = 1600L
        const val FINAL_INTERVAL_MS = 200L
    }
}
