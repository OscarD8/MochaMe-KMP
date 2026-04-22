package com.mochame.platform.providers

import com.benasher44.uuid.uuid4
import com.mochame.utils.IdGenerator
import org.koin.core.annotation.Single

@Single(binds = [IdGenerator::class])
class RealIdGenerator : IdGenerator {
    override fun nextId(): String {
        return uuid4().toString()
    }
}