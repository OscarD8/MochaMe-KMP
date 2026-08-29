package com.mochame.bio.di

import com.mochame.logger.LoggerModule
import com.mochame.platform.di.PlatformProviderModule
import com.mochame.utils.di.UtilsModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [UtilsModule::class, LoggerModule::class, PlatformProviderModule::class])
@ComponentScan("com.mochame.bio")
class BioProductionModule