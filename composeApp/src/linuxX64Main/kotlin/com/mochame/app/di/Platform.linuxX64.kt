package com.mochame.app.di

import kotlinx.cinterop.*
import platform.posix.*

class LinuxPlatform : Platform {
    // Standard Linux identification
    @OptIn(ExperimentalForeignApi::class)
    override val name: String = "Linux Native (${getUnameInfo { it.sysname }})"

    // Extracting the major kernel version (e.g., "6" from "6.8.0")
    override val version: Int = parseKernelVersion()

    // Combining OS release and Machine architecture
    @OptIn(ExperimentalForeignApi::class)
    override val deviceModel: String = "${getUnameInfo { it.release }} (${getUnameInfo { it.machine }})"

    @OptIn(ExperimentalForeignApi::class)
    private fun parseKernelVersion(): Int {
        val release = getUnameInfo { it.release }
        return try {
            // Kernel versions are usually "major.minor.patch"
            release.split(".")[0].toInt()
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Helper to safely access the POSIX uname struct.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun getUnameInfo(extractor: (utsname) -> CPointer<ByteVar>?): String {
        return memScoped {
            val uts = alloc<utsname>()
            if (uname(uts.ptr) == 0) {
                extractor(uts)?.toKString() ?: "Unknown"
            } else {
                "Unknown"
            }
        }
    }
}

actual fun getPlatform(): Platform = LinuxPlatform()