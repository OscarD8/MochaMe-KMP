package com.mochame.utils.logger

import co.touchlab.kermit.Logger
import kotlin.time.TimeMark

fun Logger.appendTag(subTag: String): Logger =
    this.withTag("${this.tag} : $subTag")

/**
 * Appends a monotonic duration to any log message.
 */
fun String.withTimer(mark: TimeMark): String =
    "$this | Duration: ${mark.elapsedNow()}"