package com.mochame.assembly.di


interface Platform {
    val name: String
    val version: Int
    val deviceModel: String
}

expect fun getPlatform(): Platform