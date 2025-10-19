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