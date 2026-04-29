package com.mochame.contract.fixtures

import com.mochame.contract.identity.IdGenerator

class FakeIdGenerator(
    private var nextIdToReturn: String? = null
) : IdGenerator {
    private var counter = 0

    override suspend fun nextId(): String {
        val id = nextIdToReturn ?: "fake-id-${++counter}"
        nextIdToReturn = null
        return id
    }
}