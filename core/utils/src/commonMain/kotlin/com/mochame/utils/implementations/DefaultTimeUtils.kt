package com.mochame.utils.implementations

import com.mochame.utils.interfaces.TimeUtils
import org.koin.core.annotation.Single
import kotlin.time.Clock
import kotlin.time.Instant

@Single(binds = [TimeUtils::class])
class DefaultTimeUtils : TimeUtils {
    override fun now(): Instant = Clock.System.now()
}