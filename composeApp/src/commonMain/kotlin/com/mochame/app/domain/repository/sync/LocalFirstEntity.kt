package com.mochame.app.domain.repository.sync

interface LocalFirstEntity<T : LocalFirstEntity<T>> {
    val id: String
    val hlc: String // This replaced lastModified
    fun withHlc(hlc: String): T
}