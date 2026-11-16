package me.jhot.swiftcurrent.shell

import me.jhot.swiftcurrent.lock.LauncherSession
import me.jhot.swiftcurrent.lock.Screen
import me.jhot.swiftcurrent.ui.applist.EdgeScrub
import me.jhot.swiftcurrent.ui.home.EdgeTouch

/**
 * What an edge swipe on Home means once the list has opened under it. Lifting leaves the list open
 * where the finger stopped, whether it scrubbed or only rested on a letter; a lift on the star or
 * cog runs their action, as on the open list. A touch the system cancels before the finger moved
 * was no deliberate press, so the list closes again at once, with no slide. Either way the opening
 * touch counts toward the lock (a double tap on the alphabet): a quick second tap pairs with it.
 */
internal class EdgeSwipe(
    private val scrub: EdgeScrub,
    private val session: LauncherSession,
) : EdgeTouch {
    override fun down(y: Float) = scrub.press(y)

    override fun move(y: Float, moved: Boolean) = scrub.move(y, moved)

    override fun up(y: Float, moved: Boolean) = scrub.release(y)

    override fun cancel() {
        if (scrub.moved) scrub.cancel() else close()
    }

    private fun close() {
        scrub.cancel()
        if (session.screen.value == Screen.AppList) session.navigate(Screen.Home)
    }
}
