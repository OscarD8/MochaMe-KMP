package com.mochame.node

import com.benasher44.uuid.uuid4
import com.mochame.sync.spi.node.IdGenerator
import org.koin.core.annotation.Single

@Single(binds = [IdGenerator::class])
class DefaultIdGenerator : IdGenerator {
    override fun nextId(): String = uuid4().toString()
}


