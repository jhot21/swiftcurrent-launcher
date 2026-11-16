package me.jhot.swiftcurrent.ui.home

import android.Manifest
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.context.DefaultMeldModes
import me.jhot.swiftcurrent.context.PairedDevice
import me.jhot.swiftcurrent.core.Condition
import me.jhot.swiftcurrent.core.ContextState
import me.jhot.swiftcurrent.ui.ScreenshotBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList

/** The "Show when..." section of the favorite edit sheet: staged conditions, pickers and permissions. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class ConditionsSectionTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val names = mutableListOf<String>()
    private val saved = mutableListOf<List<Condition>>()
    private var dismissed = 0
    private val requests = CopyOnWriteArrayList<String>()
    private val pairedLoads = CopyOnWriteArrayList<Boolean>()
    private val modeLoads = CopyOnWriteArrayList<Boolean>()

    private fun onMain() = android.os.Looper.myLooper() == android.os.Looper.getMainLooper()

    private val earbuds = Condition.Bluetooth("AA:BB:CC:DD:EE:01", "Earbuds")
    private val church = Condition.MeldMode("church")

    private fun res(id: Int, vararg args: Any): String = compose.activity.getString(id, *args)

    private fun show(
        conditions: List<Condition>? = listOf(earbuds, church),
        context: ContextState = ContextState(),
        paired: List<PairedDevice>? = listOf(PairedDevice("AA:BB:CC:DD:EE:02", "Zed speaker"), PairedDevice("AA:BB:CC:DD:EE:03", "Car")),
        modes: () -> List<String>? = { listOf("church", "work") },
        grant: (String) -> Boolean = { true },
        showCursor: Boolean = true,
    ) {
        compose.setContent {
            ScreenshotBackdrop {
                RenameSheet(
                    currentLabel = "Browser", defaultLabel = "IronFox",
                    onSave = { names += it }, onReset = {}, onDismiss = { dismissed++ },
                    showCursor = showCursor,
                    conditions = conditions,
                    context = context,
                    onConditionsChange = { saved += it },
                    loadPairedDevices = { pairedLoads += onMain(); paired },
                    loadModes = { modeLoads += onMain(); modes() },
                    requestPermission = { p, answer -> requests += p; answer(grant(p)) },
                )
            }
        }
    }

    private fun exists(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitForTag(tag: String) = compose.waitUntil(5_000) { exists(tag) }
    private fun click(tag: String) = compose.onNodeWithTag(tag).performScrollTo().performClick()
    private fun save() = click("rename-save")

    @Test
    fun renders_one_card_per_condition_with_remove_at_the_far_right() {
        show()
        compose.onAllNodesWithTag("condition-0", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("condition-1", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("condition-2", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText(res(R.string.conditions_card, res(R.string.conditions_kind_bluetooth), "Earbuds"), useUnmergedTree = true).assertExists()
        compose.onNodeWithText(res(R.string.conditions_card, res(R.string.conditions_kind_meld), "church"), useUnmergedTree = true).assertExists()
        for (i in 0..1) {
            val card = compose.onNodeWithTag("condition-$i", useUnmergedTree = true).getBoundsInRoot()
            val toggle = compose.onNodeWithTag("condition-toggle-$i", useUnmergedTree = true).getBoundsInRoot()
            val remove = compose.onNodeWithTag("condition-remove-$i").getBoundsInRoot()
            assertTrue("remove sits right of the toggle", remove.left >= toggle.right)
            // Nothing else in the card is further right than the remove button.
            assertTrue("remove is at the card's right edge", card.right - remove.right < toggle.left - card.left)
        }
    }

    @Test
    fun toggle_flips_whenTrue() {
        show()
        compose.onNodeWithTag("rename-save").assertIsNotEnabled()
        compose.onNodeWithText(res(R.string.conditions_not_connected)).performScrollTo().performClick()
        compose.onNodeWithText(res(R.string.conditions_inactive)).performScrollTo().performClick()
        compose.onNodeWithTag("rename-save").assertIsEnabled()
        save()
        assertEquals(listOf(listOf(earbuds.copy(whenTrue = false), church.copy(whenTrue = false))), saved)
        // The name did not change, so only the conditions are saved.
        assertEquals(emptyList<String>(), names)
    }

    @Test
    fun remove_drops_the_condition() {
        show()
        click("condition-remove-0")
        compose.onAllNodesWithTag("condition-1", useUnmergedTree = true).assertCountEquals(0)
        save()
        assertEquals(listOf(listOf<Condition>(church)), saved)
    }

    @Test
    fun broken_condition_shows_never_matches_message() {
        // Paired devices are known and the earbuds are not among them.
        show(context = ContextState(pairedBluetooth = setOf("AA:BB:CC:DD:EE:02"), knownModes = setOf("church")))
        compose.onNodeWithText(res(R.string.conditions_broken_device), useUnmergedTree = true).assertExists()
        compose.onAllNodesWithText(res(R.string.conditions_broken_mode), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun add_bluetooth_lists_any_first_then_paired_devices() {
        show(conditions = emptyList())
        click("condition-add")
        click("condition-add-bluetooth")
        waitForTag("bt-device-AA:BB:CC:DD:EE:03")
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), requests.toList())
        // Read off the main thread.
        assertEquals(listOf(false), pairedLoads.toList())
        val any = compose.onNodeWithTag("bt-any").getBoundsInRoot()
        val car = compose.onNodeWithTag("bt-device-AA:BB:CC:DD:EE:03").getBoundsInRoot()
        val zed = compose.onNodeWithTag("bt-device-AA:BB:CC:DD:EE:02").getBoundsInRoot()
        // Any first, then by friendly name.
        assertTrue(any.top < car.top && car.top < zed.top)
        click("bt-device-AA:BB:CC:DD:EE:02")
        compose.waitUntil(5_000) { !exists("bt-any") }
        click("condition-add")
        click("condition-add-bluetooth")
        waitForTag("bt-any")
        click("bt-any")
        save()
        assertEquals(
            listOf(listOf(Condition.Bluetooth("AA:BB:CC:DD:EE:02", "Zed speaker"), Condition.Bluetooth(null, res(R.string.conditions_any_bluetooth)))),
            saved,
        )
    }

    @Test
    fun a_device_with_a_blank_name_is_stored_under_its_address() {
        show(conditions = emptyList(), paired = listOf(PairedDevice("AA:BB:CC:DD:EE:04", " ")))
        click("condition-add")
        click("condition-add-bluetooth")
        waitForTag("bt-device-AA:BB:CC:DD:EE:04")
        click("bt-device-AA:BB:CC:DD:EE:04")
        save()
        assertEquals(listOf(listOf(Condition.Bluetooth("AA:BB:CC:DD:EE:04", "AA:BB:CC:DD:EE:04"))), saved)
    }

    @Test
    fun add_bluetooth_requests_permission_first_and_adds_nothing_when_denied() {
        show(conditions = emptyList(), grant = { false })
        click("condition-add")
        click("condition-add-bluetooth")
        compose.waitForIdle()
        assertEquals(listOf(Manifest.permission.BLUETOOTH_CONNECT), requests.toList())
        assertEquals(false, exists("bt-any"))
        assertEquals(emptyList<Boolean>(), pairedLoads.toList())
        compose.onAllNodesWithTag("condition-0", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("rename-save").assertIsNotEnabled()
    }

    @Test
    fun add_meld_requests_permission_then_lists_modes_read_at_open() {
        var modes = listOf("church", "work")
        show(conditions = emptyList(), modes = { modes })
        click("condition-add")
        click("condition-add-meld")
        waitForTag("meld-mode-work")
        assertEquals(listOf(DefaultMeldModes.PERMISSION), requests.toList())
        click("meld-mode-work")
        compose.waitUntil(5_000) { !exists("meld-mode-work") }
        // A mode made since the last open shows the next time the picker opens.
        modes = listOf("church", "work", "gym")
        click("condition-add")
        click("condition-add-meld")
        waitForTag("meld-mode-gym")
        assertEquals(2, modeLoads.size)
        assertEquals(listOf(false, false), modeLoads.toList())
        click("meld-mode-gym")
        save()
        assertEquals(listOf(listOf<Condition>(Condition.MeldMode("work"), Condition.MeldMode("gym"))), saved)
    }

    @Test
    fun adding_a_duplicate_condition_is_ignored() {
        show(conditions = listOf(church))
        click("condition-add")
        click("condition-add-meld")
        waitForTag("meld-mode-church")
        click("meld-mode-church")
        compose.waitUntil(5_000) { !exists("meld-mode-church") }
        compose.onAllNodesWithTag("condition-1", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("rename-save").assertIsNotEnabled()
    }

    @Test
    fun saving_applies_staged_conditions_and_dismiss_discards_them() {
        show()
        click("condition-remove-1")
        compose.onNodeWithTag("rename-field").performTextReplacement("Web")
        save()
        assertEquals(listOf(listOf<Condition>(earbuds)), saved)
        assertEquals(listOf("Web"), names)
    }

    @Test
    fun dismissing_discards_staged_conditions() {
        show()
        click("condition-remove-1")
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(1, dismissed)
        assertEquals(emptyList<List<Condition>>(), saved)
        assertEquals(emptyList<String>(), names)
    }

    @Test
    fun section_absent_when_conditions_null() {
        show(conditions = null)
        compose.onAllNodesWithTag("conditions-section", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("condition-add").assertCountEquals(0)
    }

    @Test
    fun an_empty_list_offers_add_without_the_must_hold_line() {
        show(conditions = emptyList())
        compose.onAllNodesWithTag("conditions-section", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithText(res(R.string.conditions_all_must_hold), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("condition-add").assertExists()
    }

    @Test
    fun all_conditions_must_hold_line_shown() {
        show()
        compose.onNodeWithText(res(R.string.conditions_all_must_hold), useUnmergedTree = true).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rename_sheet_with_conditions() {
        show(context = ContextState(pairedBluetooth = setOf("AA:BB:CC:DD:EE:02"), knownModes = setOf("church")), showCursor = false)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/snapshots/rename_sheet_conditions.png")
    }
}
