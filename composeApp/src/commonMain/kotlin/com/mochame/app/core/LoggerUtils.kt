package com.mochame.app.core

import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

fun Logger.appendTag(subTag: String): Logger =
    this.withTag("${this.tag} : $subTag")

class CleanLogWriter : LogWriter() {
    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?
    ) {
        val now = System.currentTimeMillis()
        val timeStr = formatTime(now)

        val severityChar = when (severity) {
            Severity.Verbose -> "V"
            Severity.Debug -> "D"
            Severity.Info -> "I"
            Severity.Warn -> "W"
            Severity.Error -> "E"
            Severity.Assert -> "A"
        }

        // Format: [Time] ❯ [Severity] ❯ [Tag] ❯ Message
        println("$timeStr ❯ $severityChar ❯ [$tag] ❯ $message")
    }

    private fun formatTime(millis: Long): String {
        val sss = millis % 1000
        val ss = (millis / 1000) % 60
        val mm = (millis / (1000 * 60)) % 60
        val hh = (millis / (1000 * 60 * 60)) % 24
        return "${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}.${sss.toString().padStart(3, '0')}"
    }
}