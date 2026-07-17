package com.mochame.platform.fixtures

import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.random.Random
import kotlin.time.Clock

data class TestWorkspace(
    val root: Path,
    val pending: Path,
    val committed: Path
)

private val workspaceLock = reentrantLock()
private var workspaceCounter = 0

fun createTestWorkspace(baseDir: String = "build/mocha-tests"): TestWorkspace {

    val currentCount = workspaceLock.withLock {
        workspaceCounter++
    }

    // Generate the unique ID
    val timestamp = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .let { "${it.date}_${it.hour}-${it.minute}" }
    val folderName = "mocha_test_${timestamp}__$currentCount"

    val root = Path(baseDir, folderName)

    if (!SystemFileSystem.exists(Path(baseDir))) {
        SystemFileSystem.createDirectories(Path(baseDir))
    }

    return TestWorkspace(
        root = root,
        pending = Path(root, "pending"),
        committed = Path(root, "committed")
    )
}