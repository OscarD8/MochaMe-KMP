package com.mochame.platform.test

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.time.Clock

data class TestWorkspace(
    val root: Path,
    val pending: Path,
    val committed: Path
)

fun createTestWorkspace(baseDir: String = "build/mocha-tests"): TestWorkspace {
    // 1. Generate the unique ID
    val timestamp = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .let { "${it.date}_${it.hour}-${it.minute}" }
    val salt = Random.nextInt(1000, 9999)
    val folderName = "mocha_test_${timestamp}_$salt"

    // 2. Resolve the path
    // Path(baseDir, folderName) is platform-agnostic in kotlinx-io
    val root = Path(baseDir, folderName)

    // 3. Ensure the parent directory exists
    // This is the most important part for platform stability
    if (!SystemFileSystem.exists(Path(baseDir))) {
        SystemFileSystem.createDirectories(Path(baseDir))
    }

    return TestWorkspace(
        root = root,
        pending = Path(root, "pending"),
        committed = Path(root, "committed")
    )
}