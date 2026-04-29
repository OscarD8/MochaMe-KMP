package com.mochame.logger

import com.mochame.contract.di.PlatformTag
import org.koin.core.annotation.Single

@Single
@PlatformTag
fun provideTag() = "Linux"