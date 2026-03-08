package com.mochame.app.database


import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidDatabaseHostTest {
    @Test
    fun `test robolectric and database init`() {
        // 1. Get the Shadow Application (Provided by Robolectric)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context)

        val db = getTestDatabaseBuilder().build()
        assertNotNull(db)
        assertTrue(db.isOpen.not())
    }
}