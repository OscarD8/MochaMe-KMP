package com.mochame.app.di

class JVMPlatform : Platform {
    // Standard Java version string (e.g., "Java 17.0.1")
    override val name: String = "Java ${System.getProperty("java.version")}"

    // Extracting the major version (e.g., 17) for logic gates
    override val version: Int = parseJavaVersion()

    // Combining OS and Arch for a descriptive device model
    override val deviceModel: String = "${System.getProperty("os.name")} (${System.getProperty("os.arch")})"

    private fun parseJavaVersion(): Int {
        val version = System.getProperty("java.version")
        return try {
            if (version.startsWith("1.")) {
                // Handle legacy versions like 1.8.0
                version.substring(2, 3).toInt()
            } else {
                // Handle modern versions like 17.0.1
                version.split(".")[0].toInt()
            }
        } catch (e: Exception) {
            0 // Default for unknown/corrupted environments
        }
    }
}

actual fun getPlatform(): Platform = JVMPlatform()