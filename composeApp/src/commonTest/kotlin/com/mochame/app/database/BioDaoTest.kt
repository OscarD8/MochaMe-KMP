package com.mochame.app.database

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.mochame.app.database.dao.BioDao
import com.mochame.app.database.entity.DailyContextEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

expect fun getTestDatabaseBuilder(): RoomDatabase.Builder<MochaDatabase>

/*
Simply don't run this on android environments :(
 */
class BioDaoTest {
    private lateinit var db: MochaDatabase
    private lateinit var dao: BioDao

    @BeforeTest
    fun setup() {
        db = getTestDatabaseBuilder() // Calls the expect/actual chain
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Unconfined)
            .build()
        dao = db.bioDao()
    }

    @AfterTest
    fun tearDown() {
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun verify_storing_valid_context() = runTest {
        val context = DailyContextEntity(id = "test_1", epochDay = 20500L, sleepHours = 7.5, lastModified = 1) // From BioDao.kt
        dao.upsertDailyContext(context)

        val retrieved = dao.getContextByEpochDaySync(20500L)
        assertNotNull(retrieved)
    }
}