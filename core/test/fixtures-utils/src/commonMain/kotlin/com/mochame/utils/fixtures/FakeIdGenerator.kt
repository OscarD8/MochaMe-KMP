package com.mochame.utils.fixtures

import com.mochame.sync.spi.node.IdGenerator


class FakeIdGenerator(
    private var nextIdToReturn: String? = null
) : IdGenerator {
    private var counter = 0

    override fun nextId(): String {
        val id = nextIdToReturn ?: "fake-id-${++counter}"
        nextIdToReturn = null
        return id
    }
}

