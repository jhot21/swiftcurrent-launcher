package me.jhot.swiftcurrent

import androidx.test.ext.junit.runners.AndroidJUnit4
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.shell.FakeDependencies
import me.jhot.swiftcurrent.shell.LauncherDependencies
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/** Runs the real activity against fakes, so the app's own start-up read is the baseline to count from. */
internal class FakeDependenciesApp : SwiftcurrentApp() {
    // Lazy: FakeDependencies asks ApplicationProvider for this very app, which does not exist yet in a constructor.
    val fake by lazy { FakeDependencies(LauncherData(), emptyList()) }
    override fun createDependencies(): LauncherDependencies = fake
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = FakeDependenciesApp::class)
class HomeActivityResumeTest {
    @get:org.junit.Rule
    val watchdog = WatchdogTimeout()

    // Meld sends nothing for an inactive mode that is created, renamed or deleted, and a grant made in
    // Settings arrives silently too, so every return to Home has to read the context again.
    @Test
    fun every_resume_refreshes_the_context() {
        val controller = Robolectric.buildActivity(HomeActivity::class.java).setup()
        val fake = (controller.get().application as FakeDependenciesApp).fake
        val afterFirstResume = fake.contextRefreshes.get()
        assertTrue("first resume should read", afterFirstResume >= 1)

        controller.pause().stop().restart().start().resume()

        assertEquals(afterFirstResume + 1, fake.contextRefreshes.get())
    }
}
