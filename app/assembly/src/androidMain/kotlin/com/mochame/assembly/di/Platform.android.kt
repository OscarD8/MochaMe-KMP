package com.mochame.assembly.di

import android.os.Build
import org.koin.core.annotation.Single

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val version: Int = Build.VERSION.SDK_INT
    override val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
}

@Single
actual fun getPlatform(): Platform = AndroidPlatform()