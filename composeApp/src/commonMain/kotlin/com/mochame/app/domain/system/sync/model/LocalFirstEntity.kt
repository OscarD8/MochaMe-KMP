package com.mochame.app.domain.system.sync.model

import com.mochame.app.infrastructure.sync.HLC

interface LocalFirstEntity<T : LocalFirstEntity<T>> {
    val id: String
    val hlc: HLC // This replaced lastModified
    fun withHlc(hlc: HLC): T
    fun withPhysicalTime(time: Long): T
}