package com.mochame.app.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mochame.app.database.entity.DailyContextEntity
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class AndroidDatabaseDeviceTest {
    @Test
    fun verifyDatabaseIsOnHardware() = runTest {
        // 2. Build the database using your centralized logic
        val db = getTestDatabaseBuilder().build()
        val dao = db.bioDao()

        // 3. Perform a real hardware-level transaction
        val context = DailyContextEntity(
            id = "hw_test",
            epochDay = 20500L,
            sleepHours = 7.5,
            lastModified = 1
        )
        dao.upsertDailyContext(context)

        val result = dao.getContextByEpochDaySync(20500L)
        assertNotNull(result)

        db.close()
    }
}