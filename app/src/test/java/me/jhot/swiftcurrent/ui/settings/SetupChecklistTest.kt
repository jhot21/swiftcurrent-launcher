package me.jhot.swiftcurrent.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupChecklistTest {
    private class Env(
        var home: Boolean = false,
        var contacts: Boolean = false,
        var calendar: Boolean = false,
        var lock: Boolean = false,
    ) : SetupEnv {
        override fun isDefaultHome() = home
        override fun hasContacts() = contacts
        override fun hasCalendar() = calendar
        override fun lockServiceOn() = lock
    }

    private fun done(env: Env) = SetupChecklist(env).steps().filter { it.done }.map { it.kind }

    @Test
    fun setup_steps_reflect_environment_and_mark_done() {
        assertEquals(
            listOf(StepKind.DefaultLauncher, StepKind.Contacts, StepKind.Calendar, StepKind.Accessibility),
            SetupChecklist(Env()).steps().map { it.kind },
        )
        assertEquals(emptyList<StepKind>(), done(Env()))
        assertEquals(listOf(StepKind.DefaultLauncher), done(Env(home = true)))
        assertEquals(listOf(StepKind.Contacts), done(Env(contacts = true)))
        assertEquals(listOf(StepKind.Calendar), done(Env(calendar = true)))
        assertEquals(listOf(StepKind.Accessibility), done(Env(lock = true)))
        assertEquals(listOf(StepKind.Contacts, StepKind.Accessibility), done(Env(contacts = true, lock = true)))
        assertEquals(StepKind.entries, done(Env(true, true, true, true)))
    }

    @Test
    fun progress_counts_the_done_steps_and_follows_the_environment() {
        val env = Env(contacts = true)
        assertEquals(1, SetupChecklist.doneCount(SetupChecklist(env).steps()))
        env.calendar = true
        env.home = true
        assertEquals(3, SetupChecklist.doneCount(SetupChecklist(env).steps()))
        env.lock = true
        assertEquals(4, SetupChecklist.doneCount(SetupChecklist(env).steps()))
    }
}
