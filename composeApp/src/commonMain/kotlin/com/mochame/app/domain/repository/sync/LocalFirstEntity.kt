package com.mochame.app.domain.repository.sync

import com.mochame.app.core.HLC

interface LocalFirstEntity<T : LocalFirstEntity<T>> {
    val id: String
    val hlc: HLC // This replaced lastModified
    fun withHlc(hlc: HLC): T
}