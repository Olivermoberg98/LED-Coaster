package com.example.myemptyapp.games

import com.example.myemptyapp.protocol.Rgb
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.random.Random

/** What a game needs from a coaster. */
interface CoasterController {
    val name: String

    /**
     * Shows a solid colour (the FIXED pattern); [Rgb.OFF] turns the coaster
     * dark. Suspends until the coaster has acknowledged the write. Returns
     * false if the write failed.
     */
    suspend fun showColor(color: Rgb): Boolean
}

/** A game over a fixed set of coasters. Cancel the calling coroutine to stop it. */
interface Game {
    /** Plays one round. Returns the losing coaster, or null if the game has none. */
    suspend fun play(coasters: List<CoasterController>): CoasterController?
}

/** Sends [color] to every coaster at once and returns when all have acknowledged. */
internal suspend fun List<CoasterController>.showAll(color: Rgb) = coroutineScope {
    forEach { coaster -> launch { coaster.showColor(color) } }
}

internal fun Random.nextColor() = Rgb(nextInt(256), nextInt(256), nextInt(256))
