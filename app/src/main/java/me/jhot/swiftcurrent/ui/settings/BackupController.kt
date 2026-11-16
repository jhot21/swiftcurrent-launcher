package me.jhot.swiftcurrent.ui.settings

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import me.jhot.swiftcurrent.catalog.RoomUsageStore
import me.jhot.swiftcurrent.core.Backup
import me.jhot.swiftcurrent.core.ImportResult
import me.jhot.swiftcurrent.core.LauncherStore
import me.jhot.swiftcurrent.core.orElse

sealed interface BackupOutcome {
    data object BackedUp : BackupOutcome
    data object Restored : BackupOutcome

    /** The text is not a backup this version can read; stored data is untouched. */
    data object Rejected : BackupOutcome

    /** The file could not be read or written. */
    data object Failed : BackupOutcome
}

/**
 * Back up and restore, with the blocking file and store work on [io]. [export] and [restore] take the
 * file access as lambdas so the caller decides where the bytes live (a document the owner picked,
 * or the text kept from unreadable data). Launch counts live in [usage], not the store, so they are
 * read from and restored to it; search history stays out of backups.
 */
class BackupController(
    private val store: LauncherStore,
    private val usage: RoomUsageStore,
    private val io: CoroutineDispatcher,
) {
    suspend fun export(write: (String) -> Unit): BackupOutcome = guarded {
        withContext(io) { write(Backup.export(store.load().copy(launchCounts = usage.counts()))) }
        BackupOutcome.BackedUp
    }

    suspend fun restore(read: () -> String): BackupOutcome = guarded {
        val text = withContext(io) { read() }
        when (val r = withContext(io) { Backup.restore(text, store) }) {
            is ImportResult.Ok -> {
                // The settings are already written by now, so a counts failure must not report the
                // restore as failed (that would leave the old setup on screen); counts only order the app list.
                try {
                    withContext(io) { usage.replaceCounts(r.data.launchCounts) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w("BackupController", "restored without launch counts", e)
                }
                BackupOutcome.Restored
            }
            is ImportResult.Rejected -> BackupOutcome.Rejected
        }
    }

    private suspend fun guarded(block: suspend () -> BackupOutcome): BackupOutcome = orElse(BackupOutcome.Failed) {
        block()
    }
}
