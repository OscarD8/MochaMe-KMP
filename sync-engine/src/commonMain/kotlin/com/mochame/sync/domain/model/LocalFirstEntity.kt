package com.mochame.sync.domain.model

import com.mochame.sync.infrastructure.HLC

interface LocalFirstEntity<T : LocalFirstEntity<T>> {
    val id: String
    val hlc: HLC // This replaced lastModified
    fun withHlc(hlc: HLC): T
    fun withPhysicalTime(time: Long): T
}