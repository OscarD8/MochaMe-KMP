package com.mochame.logger

import co.touchlab.kermit.Logger
import kotlin.time.TimeMark

object LogTags {
    const val APP = "Mocha"

    object Domain {
        const val METADATA = "Meta"
        const val NODE = "Node"
        const val SYNC = "Sync"
        const val AUTH = "Auth"
        const val PLATFORM = "Plat"
        const val BIO = "Bio"
        const val SIGNAL = "Sign"
        const val TELEMETRY = "Tele"
        const val PRUNE = "Prun"
        const val POLICY = "Plcy"
        const val BOOT = "Boot"

    }

    object Layer {
        const val TRANSPORT = "Trans"
        const val UI = "UI.."
        const val REPO = "Repo"
        const val DOMAIN = "Domn"
        const val DATA = "Data"
        const val INFRA = "Infr"
        const val SERI = "Seri"
        const val ORCH = "Orch"
    }
}

/**
 * Ensures the format: Platform ❯ Layer ❯ Domain ❯ Class.
 *
 * Utilizes Kermit [withTag] method to ensure each new instance points to the same
 * Logger config.
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