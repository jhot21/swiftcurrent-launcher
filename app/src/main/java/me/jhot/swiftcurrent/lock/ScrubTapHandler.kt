package me.jhot.swiftcurrent.lock

/** Glue between the scrubber's fresh touch-downs and locking. The list has already jumped on a first tap. */
class ScrubTapHandler(
    private val detector: DoubleTapDetector,
    private val lock: LockController,
    private val session: LauncherSession,
    private val nowMs: () -> Long,
    private val onLockResult: (LockResult) -> Unit,
) {
    fun onScrubTap(letter: Char) {
        when (detector.onTap(letter, nowMs())) {
            is TapOutcome.Jump -> Unit
            TapOutcome.Lock -> {
                val result = lock.lock()
                onLockResult(result)
                // Reset now rather than wait for the screen-off broadcast, so the list is gone before it is seen again.
                if (result == LockResult.Locked) session.onScreenOff()
            }
        }
    }
}
