package com.mochame.platform.fixtures

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

fun createTestWorkspace(baseDir: String = "build/mocha-tests"): TestWorkspace {
    // Generate the unique ID
    val timestamp = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .let { "${it.date}_${it.hour}-${it.minute}" }
    val salt = Random.nextInt(1000, 9999)
    val folderName = "mocha_test_${timestamp}_$salt"

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