package com.mochame.app.infrastructure.utils

import kotlin.time.TimeMark

/**
 * Appends a monotonic duration to any log message.
 */
fun String.withTimer(mark: TimeMark): String =
    "$this | Duration: ${mark.elapsedNow()}"

