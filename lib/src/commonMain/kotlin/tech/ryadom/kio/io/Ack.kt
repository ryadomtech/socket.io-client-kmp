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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

fun interface Ack {
    fun call(vararg args: Any)
}

/**
 * Represents an acknowledgment (ack) that can time out.
 */
abstract class AckWithTimeout(val timeout: Long) : Ack {
    private var job: Job? = null

    override fun call(vararg args: Any) {
        // Cancel the timeout job if it's active
        job?.cancel()
        // Clear the job reference as it's no longer needed
        job = null
        onSuccess(*args)
    }

    /**
     * Schedules a timeout for this ack.
     * If the ack is not called via `call()` within the `timeout` period,
     * the `block` will be executed and then `onTimeout()` will be called.
     *
     * @param scope The CoroutineScope to launch the timeout job in.
     * @param block A block of code to execute before `onTimeout` is called.
     */
    internal fun schedule(scope: CoroutineScope, block: () -> Unit) {
        // If already scheduled, do nothing
        if (job?.isActive == true) return

        job = scope.launch {
            delay(timeout)
            block()
            onTimeout()
        }
    }

    internal fun cancel() {
        job?.cancel()
        job = null
    }

    abstract fun onSuccess(vararg args: Any)
    abstract fun onTimeout()
}
