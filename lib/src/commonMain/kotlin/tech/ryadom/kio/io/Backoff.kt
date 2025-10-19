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

package tech.ryadom.kio.io

import io.ktor.util.date.GMTDate
import kotlin.math.floor
import kotlin.random.Random

/**
 * Implements an exponential backoff strategy, useful for retrying operations with increasing delays.
 * This helps to prevent overwhelming a service that may be temporarily unavailable.
 *
 * The delay duration is calculated based on the number of attempts, a factor, and an optional jitter
 * to randomize the delay slightly and avoid synchronized retries from multiple clients (thundering herd problem).
 *
 * Example usage:
 * ```kotlin
 * val backoff = Backoff(initialMin = 100, initialMax = 5000, initialFactor = 2, initialJitter = 0.5)
 *
 * suspend fun performActionWithRetry() {
 *     while (true) {
 *         try {
 *             // attempt the operation
 *             println("Performing action...")
 *             // simulate a failure
 *             throw IOException("Service unavailable")
 *         } catch (e: IOException) {
 *             val delay = backoff.duration()
 *             if (delay >= backoff.max) {
 *                 println("Max retries reached. Aborting.")
 *                 backoff.reset() // Reset for next time
 *                 break
 *             }
 *             println("Action failed. Retrying in $delay ms...")
 *             delay(delay)
 *         }
 *     }
 * }
 * ```
 *
 * @property initialMin The initial and minimum backoff duration in milliseconds. Defaults to 100ms.
 * @property initialMax The maximum backoff duration in milliseconds. The calculated duration will be capped at this value. Defaults to 10000ms.
 * @property initialFactor The multiplication factor for each subsequent attempt. A factor of 2 means the delay doubles each time. Must be >= 1. Defaults to 2.
 */
internal class Backoff(
    initialMin: Long = 100,
    initialMax: Long = 10000,
    initialFactor: Int = 2,
    initialJitter: Double = 0.0
) {

    private val random = Random(GMTDate().timestamp)

    var min: Long = initialMin
        set(value) {
            field = value.coerceAtLeast(0)
        }

    var max: Long = initialMax
        set(value) {
            field = value.coerceAtLeast(min)
        }

    var factor: Int = initialFactor
        set(value) {
            field = value.coerceAtLeast(1)
        }

    var jitter: Double = initialJitter
        set(value) {
            require(value in 0.0..<1.0) { "jitter must be between 0 and 1" }
            field = value
        }

    var attempts: Int = 0
        private set

    fun duration(): Long {
        var base = min.toDouble()

        repeat(attempts) {
            base *= factor
        }

        this.attempts++

        if (jitter != 0.0) {
            val rand = random.nextDouble()
            val deviation = rand * jitter * base

            base = if ((floor(rand * 10).toInt() and 1) == 0) {
                base - deviation
            } else {
                base + deviation
            }
        }

        return base.coerceIn(min.toDouble(), max.toDouble()).toLong()
    }

    fun reset() {
        attempts = 0
    }
}