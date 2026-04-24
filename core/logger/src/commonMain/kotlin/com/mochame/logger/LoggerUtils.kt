package com.mochame.logger

import co.touchlab.kermit.Logger
import kotlin.time.TimeMark

object LogTags {
    const val APP = "Mocha"

    object Domain {
        const val METADATA = "Metadata"
        const val SYNC = "Sync"
        const val AUTH = "Auth"
        const val BIO = "Bio"
        const val SIGNAL = "Signal"
        const val TELEMETRY = "Telem"
        const val PRUNE = "Prune"
        const val EXECUTE = "Execute"
    }

    object Layer {
        const val UI = "UI.."
        const val REPO = "Repo"
        const val DOMAIN = "Domn"
        const val DATA = "Data"
        const val INFRA = "Infr"
        const val ORCH = "Orch"
        const val BOOT = "Boot"
    }
}

/**
 * Ensures the exact format: Platform ❯ Layer ❯ Domain ❯ Class
 */
fun Logger.withTags(layer: String, domain: String, className: String? = null): Logger {
    val base = "${this.tag} ❯ $layer ❯ $domain"
    return if (className != null) this.withTag("$base ❯ $className") else this.withTag(base)
}

/**
 * Appends a duration to any log message.
 */
fun String.withTimer(mark: TimeMark): String =
    "$this | Duration: ${mark.elapsedNow()}"