package me.jhot.swiftcurrent.catalog

import android.content.pm.ApplicationInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsArchivedTest {
    @Test
    fun an_app_that_is_no_longer_installed_is_archived() {
        assertTrue(isArchived(ApplicationInfo.FLAG_HAS_CODE))
    }

    @Test
    fun an_installed_app_is_not() {
        assertFalse(isArchived(ApplicationInfo.FLAG_INSTALLED or ApplicationInfo.FLAG_HAS_CODE))
    }
}
