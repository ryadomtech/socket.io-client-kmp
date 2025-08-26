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

package tech.ryadom.socketio.client.util

/**
 * Interface for logging messages within the Kio library.
 * This interface provides a flexible way to integrate with different logging frameworks.
 *
 * Implement this interface to customize how log messages are handled.
 * By default, no logging is performed unless a concrete implementation is provided.
 *
 * The `debug`, `info`, `warn`, and `error` functions are convenience methods
 * that delegate to the `log` function with the appropriate [LogLevel].
 *
 * The `message` parameter in the logging functions is a lambda (`() -> String`)
 * to allow for lazy evaluation of the log message. This can improve performance
 * if the message construction is expensive and the log level is not enabled.
 */
interface KioLogger {

    fun debug(message: () -> String) {
        log(level = LogLevel.DEBUG, message = message(), cause = null)
    }

    fun info(message: () -> String) {
        log(level = LogLevel.INFO, message = message(), cause = null)
    }

    fun warn(cause: Throwable? = null, message: () -> String) {
        log(level = LogLevel.WARN, message = message(), cause = cause)
    }

    fun error(cause: Throwable? = null, message: () -> String) {
        log(level = LogLevel.ERROR, message = message(), cause = cause)
    }

    fun log(level: LogLevel, message: String, cause: Throwable?)

}

enum class LogLevel {
    DEBUG, INFO, WARN, ERROR;
}