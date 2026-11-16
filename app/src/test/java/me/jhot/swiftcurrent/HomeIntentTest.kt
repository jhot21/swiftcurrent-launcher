package me.jhot.swiftcurrent

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HomeIntentTest {
    @Test
    fun homeIntentResolvesToHomeActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setPackage(context.packageName)
        val resolved = context.packageManager.resolveActivity(intent, 0)
        assertEquals("me.jhot.swiftcurrent.HomeActivity", resolved?.activityInfo?.name)
    }
}
