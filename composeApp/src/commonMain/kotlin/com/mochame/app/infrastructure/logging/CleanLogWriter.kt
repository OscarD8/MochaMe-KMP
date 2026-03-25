package com.mochame.app.infrastructure.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

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

        val color = when (severity) {
            Severity.Verbose, Severity.Debug -> "\u001B[90m" // Gray
            Severity.Info -> "\u001B[32m"    // Green
            Severity.Warn -> "\u001B[33m"    // Yellow
            Severity.Error -> "\u001B[31m"   // Red
            Severity.Assert -> "\u001B[35m"  // Magenta
        }
        val reset = "\u001B[0m"

        // Format: [Time] ❯ [Severity] ❯ [Tag: Mocha : Layer , Domain : Component] ❯ Message
        println("${color}$timeStr ❯ $severityChar ❯ [$tag] ❯ $message${reset}")
    }

    private fun formatTime(millis: Long): String {
        val sss = millis % 1000
        val ss = (millis / 1000) % 60
        val mm = (millis / (1000 * 60)) % 60
        val hh = (millis / (1000 * 60 * 60)) % 24
        return "${hh.toString().padStart(2, '0')}:${mm.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}.${sss.toString().padStart(3, '0')}"
    }
}