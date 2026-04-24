package com.mochame.logger

import com.mochame.di.PlatformTag
import org.koin.core.annotation.Single


@Single
@PlatformTag
fun provideTag() = "Android"