package com.mochame.app.infrastructure.utils

import kotlinx.coroutines.sync.Mutex
import kotlin.time.TimeMark


/**
 * Executes [action] only if the mutex can be acquired immediately.
 * Automatically unlocks after [action] completes or fails.
 * Returns the result of [action], or null if the lock was not acquired.
 */
inline fun <T> Mutex.withTryLock(owner: Any? = null, action: () -> T): T? {
    if (tryLock(owner)) {
        try {
            return action()
        } finally {
            unlock(owner)
        }
    }
    return null
}

/**
 * Appends a monotonic duration to any log message.
 */
fun String.withTimer(mark: TimeMark): String =
    "$this | Duration: ${mark.elapsedNow()}"

