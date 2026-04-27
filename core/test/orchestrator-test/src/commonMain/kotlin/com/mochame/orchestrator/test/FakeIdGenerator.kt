package com.mochame.orchestrator.test

import com.mochame.utils.IdGenerator

class FakeIdGenerator(
    private var nextIdToReturn: String? = null
) : IdGenerator {

    private var counter = 0

    /**
     * Forces the next call to return a specific ID.
     */
    fun forceNextId(id: String) {
        nextIdToReturn = id
    }

    override fun nextId(): String {
        val id = nextIdToReturn ?: "fake-id-${++counter}"
        nextIdToReturn = null // Reset after one-time use
        return id
    }
}