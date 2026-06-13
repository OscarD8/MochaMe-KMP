package com.mochame.utils

import com.benasher44.uuid.uuid4
import com.mochame.contract.node.IdGenerator
import org.koin.core.annotation.Single

@Single(binds = [IdGenerator::class])
class DefaultIdGenerator : IdGenerator {
    override suspend fun nextId(): String = uuid4().toString()
}


