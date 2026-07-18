package com.mochame.utils.implementations

import com.mochame.utils.interfaces.TimeProvider
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Instant

@Single(binds = [TimeProvider::class])
class DefaultTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}