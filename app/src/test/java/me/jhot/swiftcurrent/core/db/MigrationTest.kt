package me.jhot.swiftcurrent.core.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), SwiftcurrentDatabase::class.java)

    @Test
    fun migrating_1_to_2_keeps_rows_and_leaves_actionId_null() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("INSERT INTO selection (id, resultId, kind, query, source, at) VALUES (1, 'app:a/.A', 'app', 'a', 'search', 100)")
            execSQL("INSERT INTO launch_count (component, count) VALUES ('a/.A', 3)")
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test", 2, true)

        db.query("SELECT resultId, kind, query, source, at, actionId FROM selection").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("app:a/.A", c.getString(0))
            assertEquals("app", c.getString(1))
            assertEquals("a", c.getString(2))
            assertEquals("search", c.getString(3))
            assertEquals(100L, c.getLong(4))
            assertTrue(c.isNull(5))
            assertEquals(1, c.count)
        }
        db.query("SELECT component, count FROM launch_count").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("a/.A", c.getString(0))
            assertEquals(3, c.getInt(1))
        }
    }
}
