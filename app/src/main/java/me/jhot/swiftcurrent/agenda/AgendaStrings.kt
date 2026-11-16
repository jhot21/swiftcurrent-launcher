package me.jhot.swiftcurrent.agenda

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import me.jhot.swiftcurrent.R

class ResourceAgendaStrings(private val res: Resources) : AgendaStrings {
    override val today get() = res.getString(R.string.agenda_today)
    override val tomorrow get() = res.getString(R.string.agenda_tomorrow)
    override val noTitle get() = res.getString(R.string.agenda_no_title)
    override val now get() = res.getString(R.string.agenda_now)
    override fun inMinutes(n: Long) = res.getString(R.string.agenda_in_minutes, n)
    override fun inHours(n: Long) = res.getString(R.string.agenda_in_hours, n)
    override fun inDays(n: Long) = res.getString(R.string.agenda_in_days, n)
    override fun nextLine(title: String, whenText: String) = res.getString(R.string.agenda_next_line, title, whenText)
    override fun nextLineMore(title: String, whenText: String, more: Int) =
        res.getString(R.string.agenda_next_line_more, title, whenText, more)
    override fun detail(timeRange: String, relative: String) = res.getString(R.string.agenda_detail, timeRange, relative)
}

@Composable
fun rememberAgendaFormatter(): AgendaFormatter {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) { AgendaFormatter(ResourceAgendaStrings(context.resources)) }
}
