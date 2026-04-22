package com.mochame.platform.modules

import com.benasher44.uuid.uuid4
import com.mochame.utils.IdGenerator
import org.koin.core.annotation.Single

@Single(binds = [IdGenerator::class])
class UuidGenerator : IdGenerator {
    override fun nextId(): String = uuid4().toString()
}