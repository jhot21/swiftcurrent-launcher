package me.jhot.swiftcurrent.shortcuts

import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.LauncherStore

/** In-memory store counting writes, so tests can assert that a rejected result persisted nothing. */
class MemoryLauncherStore(initial: LauncherData = LauncherData()) : LauncherStore {
    @Volatile var data = initial

    @Volatile var writes = 0
    override suspend fun load() = data
    private val lock = Any()
    override suspend fun update(transform: (LauncherData) -> LauncherData): LauncherData =
        synchronized(lock) { transform(data).also { data = it; writes++ } }
}
