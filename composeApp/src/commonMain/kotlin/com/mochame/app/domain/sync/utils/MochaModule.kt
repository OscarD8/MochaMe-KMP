package com.mochame.app.domain.sync.utils

enum class MochaModule(val tag: String) {
    BIO("bio"),
    SIGNAL("signal"),
    TELEMETRY("telemetry");

    companion object {
        val allTags get() = entries.map { it.tag }
    }
}