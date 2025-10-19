package tech.ryadom.kio

import tech.ryadom.kio.client.io.Backoff
import kotlin.math.pow
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BackoffTest {

    @Test
    fun durationShouldIncreaseTheBackoff() {
        val b = Backoff()

        assertTrue(100L == b.duration())
        assertTrue(200L == b.duration())
        assertTrue(400L == b.duration())
        assertTrue(800L == b.duration())

        b.reset()
        assertTrue(100L == b.duration())
        assertTrue(200L == b.duration())
    }

    @Test
    fun durationOverflow() {
        repeat(10) {
            val b = Backoff()
            b.min = 100
            b.max = 10000
            b.jitter = 0.5

            repeat(50) { j ->
                val base = (100L * (2.0.pow(j.toDouble()))).toLong()
                val deviation = (base * 0.5).toLong()

                val min = minOf(base - deviation, 10000L)
                val max = minOf(base + deviation, 10001L)

                val duration = b.duration()

                assertTrue(
                    "$min <= $duration < $max",
                    duration >= min && duration < max
                )
            }
        }
    }

    @Test
    fun ensureJitterIsValid() {
        assertFailsWith(IllegalArgumentException::class) {
            val b = Backoff()
            b.jitter = 2.0
        }
    }
}