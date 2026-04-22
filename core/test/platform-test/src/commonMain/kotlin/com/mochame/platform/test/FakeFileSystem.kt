package com.mochame.platform.test

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.io.files.Path
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.time.Clock

data class TestWorkspace(
    val root: Path,
    val pending: Path,
    val committed: Path
)

fun createTestWorkspace(): TestWorkspace {
    val timestamp = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date

    val int = Random.nextInt(1000, 9999)
    val folderName = "mocha_test_${timestamp}_$int"

    val root = Path(SystemTemporaryDirectory, folderName)
    val pending = Path(root, "pending")
    val committed = Path(root, "committed")

    return TestWorkspace(root, pending, committed)
}