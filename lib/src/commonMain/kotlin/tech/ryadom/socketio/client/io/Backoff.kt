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

package tech.ryadom.socketio.client.io

import io.ktor.util.date.GMTDate
import kotlin.math.pow
import kotlin.random.Random

/**
 * Configurable backoff timer implementation.
 *
 * @property jitter Randomness factor between 0.0 and 1.0 [default: 0.0]
 * @property factor Multiplication factor for exponential backoff [default: 2.0]
 * @property min Initial timeout in milliseconds [default: 100]
 * @property max Maximum timeout in milliseconds [default: 10000]
 */
class Backoff(
    jitter: Double = 0.0,
    private val factor: Double = 2.0,
    var min: Long = 100,
    var max: Long = 10_000,
) {
    private val random = Random(GMTDate().timestamp)

    init {
        require(jitter in 0.0..1.0) { "Jitter must be between 0.0 and 1.0" }
    }

    var attempts: Int = 0
        private set

    var jitter: Double = jitter
        set(value) {
            require(value in 0.0..1.0) { "Jitter must be between 0.0 and 1.0" }
            field = value
        }

    /**
     * Calculates the next backoff duration with exponential growth and optional jitter.
     * @return The calculated duration in milliseconds, clamped between min and max
     */
    val duration: Long
        get() {
            val baseDuration = (min * factor.pow(attempts++)).toLong()
            val durationWithJitter = if (jitter > 0.0) applyJitter(baseDuration) else baseDuration
            return durationWithJitter.coerceIn(min, max)
        }

    /**
     * Resets the attempt counter to zero.
     * @return The number of attempts before reset
     */
    fun reset(): Int = attempts.also { attempts = 0 }

    private fun applyJitter(baseDuration: Long): Long {
        val randomValue = random.nextDouble()
        val deviation = randomValue * jitter * baseDuration
        return if (random.nextBoolean()) {
            (baseDuration - deviation).toLong()
        } else {
            (baseDuration + deviation).toLong()
        }
    }
}