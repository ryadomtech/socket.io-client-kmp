/*
    MIT License

    Copyright (c) 2025 Ryadom Tech

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all
    copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
    SOFTWARE.
*/

package tech.ryadom.kio

import tech.ryadom.kio.io.Backoff
import kotlin.math.pow
import kotlin.test.DefaultAsserter.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackoffTest {

    @Test
    fun durationShouldIncreaseTheBackoff() {
        val b = Backoff()

        assertEquals(100L, b.duration())
        assertEquals(200L, b.duration())
        assertEquals(400L, b.duration())
        assertEquals(800L, b.duration())

        b.reset()
        assertEquals(100L, b.duration())
        assertEquals(200L, b.duration())
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
                    duration in min..<max
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