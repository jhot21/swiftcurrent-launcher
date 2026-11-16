package me.jhot.swiftcurrent.ui.settings

/** The four things first-run setup helps with, in the order they are listed. */
enum class StepKind { DefaultLauncher, Contacts, Calendar, Accessibility }

data class SetupStep(val kind: StepKind, val done: Boolean)

/** What the device says about each step; read off the main thread (the role check is a binder call). */
interface SetupEnv {
    fun isDefaultHome(): Boolean
    fun hasContacts(): Boolean
    fun hasCalendar(): Boolean
    fun lockServiceOn(): Boolean
}

class SetupChecklist(private val env: SetupEnv) {
    fun steps(): List<SetupStep> = listOf(
        SetupStep(StepKind.DefaultLauncher, env.isDefaultHome()),
        SetupStep(StepKind.Contacts, env.hasContacts()),
        SetupStep(StepKind.Calendar, env.hasCalendar()),
        SetupStep(StepKind.Accessibility, env.lockServiceOn()),
    )

    companion object {
        fun doneCount(steps: List<SetupStep>): Int = steps.count { it.done }
    }
}
