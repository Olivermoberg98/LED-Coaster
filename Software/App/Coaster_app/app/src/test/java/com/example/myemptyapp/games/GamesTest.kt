package com.example.myemptyapp.games

import com.example.myemptyapp.protocol.Rgb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class GamesTest {

    private data class Shown(val timeMs: Long, val coaster: String, val color: Rgb)

    /** Records every colour with the virtual time its write completed. */
    private class FakeCoaster(
        override val name: String,
        private val scope: TestScope,
        private val log: MutableList<Shown>,
        private val writeDelayMs: Long = 0
    ) : CoasterController {
        override suspend fun showColor(color: Rgb): Boolean {
            delay(writeDelayMs)
            log.add(Shown(scope.currentTime, name, color))
            return true
        }
    }

    private fun TestScope.coasters(count: Int, log: MutableList<Shown>, writeDelayMs: Long = 0) =
        List(count) { FakeCoaster("C$it", this, log, writeDelayMs) }

    @Test
    fun nattDuellen_lightsAllWhiteThenOneGoesDarkThenAll() {
        repeat(30) { seed ->
            runTest {
                val log = mutableListOf<Shown>()
                val players = coasters(3, log)

                assertNull(NattDuellen(Random(seed)).play(players))

                assertEquals(7, log.size)
                val (white, rest) = log.partition { it.color == Rgb.WHITE }
                assertEquals(players.map { it.name }.toSet(), white.map { it.coaster }.toSet())
                assertTrue(white.all { it.timeMs == 0L })

                val firstOff = rest.first()
                assertTrue("seed $seed: ${firstOff.timeMs}", firstOff.timeMs in 5000L..10000L)
                val allOff = rest.drop(1)
                assertEquals(3, allOff.size)
                assertTrue(allOff.all { it.color == Rgb.OFF && it.timeMs == firstOff.timeMs + 3000 })
            }
        }
    }

    @Test
    fun randomDrink_bouncesFasterNeverRepeatsAndEndsOnTheLoser() {
        repeat(30) { seed ->
            runTest {
                val log = mutableListOf<Shown>()
                val players = coasters(3, log)

                val loser = RandomDrink(Random(seed), testScheduler.timeSource).play(players)

                // Starts with every coaster dark
                assertTrue(log.take(3).all { it.color == Rgb.OFF && it.timeMs == 0L })

                val lit = log.filter { it.color != Rgb.OFF }
                lit.zipWithNext().forEach { (a, b) -> assertNotEquals("seed $seed", a.coaster, b.coaster) }

                // Ends with everything dark except the loser
                val last = log.last()
                assertEquals(loser.name, last.coaster)
                assertNotEquals(Rgb.OFF, last.color)
                assertTrue(log.dropLast(1).takeLast(3).all { it.color == Rgb.OFF })

                val gaps = lit.dropLast(1).zipWithNext { a, b -> b.timeMs - a.timeMs }
                assertEquals(1600L, gaps.first())
                assertTrue(gaps.all { it in 200L..1600L })
                gaps.zipWithNext().forEach { (a, b) -> assertTrue("seed $seed: $gaps", b <= a) }

                assertTrue("seed $seed: ${last.timeMs}", last.timeMs in 20_000L..(25_000L + 1600L))
            }
        }
    }

    @Test
    fun randomDrink_lightsOnlyAfterTheOffWritesComplete() = runTest {
        val log = mutableListOf<Shown>()
        val players = coasters(3, log, writeDelayMs = 50)

        RandomDrink(Random(1), testScheduler.timeSource).play(players)

        // Each bounce: the other two go dark in parallel, then one lights
        var lastOff = -1L
        for (shown in log) {
            if (shown.color == Rgb.OFF) {
                lastOff = shown.timeMs
            } else {
                assertEquals(lastOff + 50, shown.timeMs)
            }
        }
    }

    @Test
    fun randomDrink_withOneCoasterPlaysAndLoses() = runTest {
        val log = mutableListOf<Shown>()
        val players = coasters(1, log)

        assertSame(players.single(), RandomDrink(Random(3), testScheduler.timeSource).play(players))
    }

    @Test
    fun cancelledGameSendsNothingMore() = runTest {
        val log = mutableListOf<Shown>()
        val players = coasters(3, log)

        val job = launch { RandomDrink(Random(7), testScheduler.timeSource).play(players) }
        advanceTimeBy(5000)
        job.cancel()
        val sent = log.size
        advanceUntilIdle()

        assertTrue(sent > 0)
        assertEquals(sent, log.size)
    }

    @Test
    fun gamesRejectAnEmptyCoasterList() = runTest {
        for (game in listOf(NattDuellen(), RandomDrink())) {
            val error = runCatching { game.play(emptyList()) }.exceptionOrNull()
            assertTrue("$game: $error", error is IllegalArgumentException)
        }
    }
}
