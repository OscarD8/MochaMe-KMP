package com.mochame.app.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File


class JvmBioDaoTest : SharedBioDaoTest() {
    // This makes the class "reachable" for the JUnit 5 scan from command line
    @kotlin.test.Test
    fun engineWarmup() {
        assert(true)
    }

    override fun createDatabase(): MochaDatabase {
        val dbFile = File.createTempFile("test-mocha", ".db")
        dbFile.deleteOnExit()

        return Room.databaseBuilder<MochaDatabase>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
    }
}