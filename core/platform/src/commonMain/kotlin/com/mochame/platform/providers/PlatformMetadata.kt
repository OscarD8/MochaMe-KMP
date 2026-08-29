package com.mochame.platform.providers

interface PlatformMetadata {
    val name: String
    val version: Int
    val deviceModel: String
}

expect fun getPlatformMetadata(): PlatformMetadata