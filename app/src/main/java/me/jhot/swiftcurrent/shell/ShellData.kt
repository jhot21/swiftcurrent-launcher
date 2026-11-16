package me.jhot.swiftcurrent.shell

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.catalog.AppInfo
import me.jhot.swiftcurrent.core.LauncherData
import me.jhot.swiftcurrent.core.Renames

/**
 * What the shell shows, loaded off the main thread. Usage loads from the database alongside it and
 * [ready] does not wait for that: a launch made first is kept when the stored counts arrive.
 */
class ShellData(private val deps: LauncherDependencies, private val io: CoroutineDispatcher) {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _data = MutableStateFlow(LauncherData())
    val data: StateFlow<LauncherData> = _data.asStateFlow()

    // The installed apps as the system names them; only [namedApps] is published, since every screen
    // shows the owner's names.
    private val installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _namedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /**
     * The installed apps with the owner's names applied (each keeps the system name in `systemLabel`):
     * what every screen shows and sorts by, so the list, search, Home and the editor cannot disagree.
     */
    val namedApps: StateFlow<List<AppInfo>> = _namedApps.asStateFlow()

    private fun setData(d: LauncherData) {
        _data.value = d
        refreshNamed()
    }

    private fun setApps(a: List<AppInfo>) {
        installedApps.value = a
        refreshNamed()
    }

    // What the last pass named from, so a write that cannot change a name (a settings toggle) skips the walk.
    private var namedFrom: Triple<Any, Any, Any>? = null
    private var namedFromApps: List<AppInfo>? = null

    /**
     * Data is published from whichever thread wrote it and apps from the shell's own scope, so two passes can
     * overlap. Reading both inputs and publishing under one lock means the pass that runs last has read the
     * newest of each, so an older pass can never publish over a newer one.
     */
    @Synchronized
    private fun refreshNamed() {
        val d = _data.value
        val apps = installedApps.value
        val key = Triple<Any, Any, Any>(d.appNames, d.favorites, d.folders)
        // Names depend on the apps, the per-app names and the entries a legacy name may still sit on, nothing else.
        if (apps === namedFromApps && key == namedFrom) return
        namedFromApps = apps
        namedFrom = key
        _namedApps.value = apps.map { app ->
            Renames.appNameOf(d, app.component)?.let { app.copy(label = it) } ?: app
        }
    }

    /** Set once the first-run check has run, so a screen lock does not bring setup back; lives with the process. */
    var setupPrompted = false

    private val _loadFailed = MutableStateFlow(false)

    /** True when a boot-time load failed and the shell is showing fallback data. */
    val loadFailed: StateFlow<Boolean> = _loadFailed.asStateFlow()

    // As the default home app a throw here would crash on boot and loop, so every step falls back.
    private suspend fun <T> guarded(what: String, fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("ShellData", "$what failed", e)
        _loadFailed.value = true
        fallback
    }

    /** Runs for the life of the shell: first load, then follows package changes. */
    suspend fun run() = coroutineScope {
        // Not awaited: Home must not wait on the database. Set only once it finishes, so a run cancelled
        // part-way (the activity went away) leaves the next run to try again.
        if (!hydrated) {
            launch(io) {
                deps.usage.hydrate()
                hydrated = true
            }
        }
        // Held by the application: an activity that comes back (recreated) finds it loaded and only follows changes.
        if (!_ready.value) withContext(io) {
            setData(guarded("loading the store", LauncherData()) { deps.store.load() })
            setApps(guarded("loading apps", emptyList()) { deps.catalog.apps() })
        }
        _ready.value = true
        guarded("following app changes", Unit) {
            deps.catalog.changes.collect {
                setApps(guarded("reloading apps", installedApps.value) { withContext(io) { deps.catalog.apps() } })
            }
        }
    }

    @Volatile private var hydrated = false

    // Writes and reloads share one fair lock taken before the hop to IO, so edits land in the order
    // they were made and a reload after the editor closes sees every one of them.
    private val storeLock = Mutex()

    /**
     * Picks up a write the shell did not make itself: a restore replaces the whole document behind
     * [update]'s back and must report whether the write failed, so it cannot go through [update].
     */
    suspend fun reloadData() {
        setData(
            guarded("reloading the store", _data.value) {
                storeLock.withLock { withContext(io) { deps.store.load() } }
            },
        )
    }

    /**
     * Reads the store again and says whether it really did. [reloadData] falls back to what it already
     * had, which is right for Home but wrong for the editor: an editor built on data it could not
     * refresh would write the older list back over newer edits.
     */
    suspend fun reloadDataStrictly(): Boolean = try {
        val fresh = storeLock.withLock { withContext(io) { deps.store.load() } }
        setData(fresh)
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("ShellData", "reloading the store failed", e)
        false
    }

    /**
     * A partial write that survives its caller being cancelled (a screen lock while editing), so a
     * change the owner saw applied is never lost. The written data is published at once, so nothing
     * has to read the store again to see it. Failures are logged and flag [loadFailed]; the data
     * already shown stays.
     */
    suspend fun update(transform: (LauncherData) -> LauncherData): LauncherData {
        val written = withContext(NonCancellable) {
            guarded<LauncherData?>("saving", null) {
                storeLock.withLock { withContext(io) { deps.store.update(transform) } }
            }
        }
        return written?.also(::setData) ?: _data.value
    }
}
