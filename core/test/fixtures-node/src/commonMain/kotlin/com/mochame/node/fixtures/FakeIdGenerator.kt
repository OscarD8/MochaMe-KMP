package com.mochame.node.fixtures

import com.mochame.sync.spi.node.IdGenerator
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock

class FakeIdGenerator : IdGenerator {

    private val lock = reentrantLock()

    private var _counter = 0
    private var _nextIdToReturn: String? = null

    var nextIdToReturn: String?
        get() = lock.withLock { _nextIdToReturn }
        set(value) = lock.withLock { _nextIdToReturn = value }

    val counter: Int
        get() = lock.withLock { _counter }

    override fun nextId() = lock.withLock {
        val id = _nextIdToReturn ?: "fake-id-${++_counter}"
        _nextIdToReturn = null
        id
    }

    fun reset() = lock.withLock {
        _nextIdToReturn = null
        _counter = 0
    }
}