package com.mochame.platform.di

import com.mochame.sync.spi.infrastructure.DigestFactory
import com.mochame.sync.spi.infrastructure.DigestState
import org.koin.core.annotation.Factory
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

@KoinApplication(modules = [PlatformTestModule::class])
class PlatformTestApp

@Module
class PlatformTestModule

@Factory
class HasherProviderTestEnv(
    val digestFactory: DigestFactory,
    val digestState: DigestState
)