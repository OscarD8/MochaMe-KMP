package com.mochame.bio.di

import com.mochame.logger.LoggerModule
import com.mochame.platform.di.CommonPlatformModule
import com.mochame.utils.di.UtilsModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [LoggerModule::class, CommonPlatformModule::class, UtilsModule::class])
@ComponentScan("com.mochame.bio.infrastructure")
class BioProductionModule