package com.mochame.app.infrastructure.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

object LogTags {
    const val APP = "Mocha"

    object Domain {
        const val SYNC = "Sync"
        const val AUTH = "Auth"
        const val BIO = "Bio"
        const val SIGNAL = "Signal"
        const val TELEMETRY = "Telem"
    }

    object Layer {
        const val UI = "UI"
        const val REPO = "Repo"
        const val DATA = "Data"
        const val INFRA = "Infra"
        const val ORCH = "Orch"
        const val BOOT = "Boot"
    }
}

fun Logger.appendTag(subTag: String): Logger =
    this.withTag("${this.tag} : $subTag")

