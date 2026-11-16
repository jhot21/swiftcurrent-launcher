package me.jhot.swiftcurrent.shell

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.jhot.swiftcurrent.core.LauncherData
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What the shell publishes after a write: the write's own result, never a second read. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ShellDataTest {
    private suspend fun loaded(): Pair<FakeDependencies, ShellData> {
        val deps = FakeDependencies(LauncherData(), emptyList())
        val model = ShellData(deps, Dispatchers.Unconfined)
        model.run()
        return deps to model
    }

    @Test
    fun update_publishes_the_change() = runBlocking {
        val (_, model) = loaded()

        model.update { it.copy(hiddenApps = it.hiddenApps + "pkg/.Main") }

        assertEquals(setOf("pkg/.Main"), model.data.value.hiddenApps)
    }

    @Test
    fun update_reads_the_store_only_for_the_write_itself() = runBlocking {
        val (deps, model) = loaded()
        val loadsBefore = deps.fakeStore.loads

        model.update { it.copy(hiddenApps = it.hiddenApps + "pkg/.Main") }

        assertEquals(loadsBefore, deps.fakeStore.loads)
    }

    @Test
    fun startup_decodes_the_store_once_and_hydrates_counts_from_the_database() = runBlocking {
        val deps = FakeDependencies(LauncherData(launchCounts = mapOf("pkg/.Main" to 4)), emptyList())

        ShellData(deps, Dispatchers.Unconfined).run()

        assertEquals(1, deps.fakeStore.loads)
        assertEquals(mapOf("pkg/.Main" to 4), deps.usage.counts())
    }
}
