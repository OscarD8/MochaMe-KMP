package com.mochame.app.di

import android.os.Build

class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
    override val version: Int = Build.VERSION.SDK_INT
    override val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}"
}

actual fun getPlatform(): Platform = AndroidPlatform()