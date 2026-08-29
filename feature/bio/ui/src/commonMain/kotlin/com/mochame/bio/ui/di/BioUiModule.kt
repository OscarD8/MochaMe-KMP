package com.mochame.bio.ui.di

import com.mochame.bio.di.BioProductionModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [BioProductionModule::class])
@ComponentScan("com.mochame.bio.ui")
class BioUiProductionModule