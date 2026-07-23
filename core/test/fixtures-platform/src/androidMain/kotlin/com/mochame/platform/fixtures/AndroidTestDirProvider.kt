package com.mochame.platform.fixtures

actual fun testWorkspaceBaseDir(): String {
    // On device: use the app's cache directory via tmpdir
    // On host (Robolectric): build/ is fine but tmpdir is safer
    return System.getProperty("java.io.tmpdir")
        ?: "/data/local/tmp"
}