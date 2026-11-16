package me.jhot.swiftcurrent.ui.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import me.jhot.swiftcurrent.R
import me.jhot.swiftcurrent.WatchdogTimeout
import me.jhot.swiftcurrent.search.Action
import me.jhot.swiftcurrent.search.Kind
import me.jhot.swiftcurrent.search.LaunchOutcome
import me.jhot.swiftcurrent.search.SearchEngine
import me.jhot.swiftcurrent.search.SearchResult
import me.jhot.swiftcurrent.search.SearchSource
import me.jhot.swiftcurrent.shortcuts.ShortcutAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi", sdk = [34])
class SearchScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    // A hang (an animation that never settles) fails the test instead of stalling the gate.
    @get:Rule
    val timeout = WatchdogTimeout(seconds = 60)

    private val launched = CopyOnWriteArrayList<String>()
    private var outcome: LaunchOutcome = LaunchOutcome.Launched

    private fun action(label: String, id: String) = Action(label, null) {
        launched += id
        outcome
    }

    private fun shortcut(name: String) = SearchResult(
        "shortcut:$name", "Tasker: $name", "Shortcuts", Kind.Shortcut, null, action("Open", "shortcut:$name"),
    )

    private val app = SearchResult("app:bed", "Bedside Clock", "Apps", Kind.App, null, action("Open", "app:bed"))
    private val contact = SearchResult(
        "contact:1", "Alex Example", "Contacts", Kind.Contact, null, action("Open", "contact:1"),
        secondary = listOf(action("Call", "call:1"), action("Message", "sms:1"), action("WhatsApp", "wa:1")),
    )
    private val hidden = SearchResult(
        "app:hid", "Bed Hidden", "Apps", Kind.App, null, action("Open", "app:hid"), badge = "Hidden",
    )

    // The ranking is the engine's job; this source answers in the order a ranked list would have.
    private fun engineOf(vararg results: SearchResult) = SearchEngine(
        listOf(object : SearchSource {
            override val name = "fake"
            override suspend fun search(query: String) =
                results.filter { it.label.contains(query, ignoreCase = true) }
        }),
    )

    private val tasker = arrayOf(shortcut("ExitNightMode"), shortcut("CreateAlarm"), shortcut("Bedtime"))

    private var closed = 0
    private var rowBodies = 0

    /** Each pick reported, as "id|query", with whether the screen had closed yet. */
    private val picked = CopyOnWriteArrayList<String>()

    /** The action each pick ran, as "label|recentId". */
    private val pickedActions = CopyOnWriteArrayList<String>()

    private fun show(
        engine: SearchEngine = engineOf(*tasker),
        access: ShortcutAccess = ShortcutAccess.Granted,
        contactsDenied: Boolean = false,
        refresh: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        showCursor: Boolean = true,
        onHideAction: (suspend (SearchResult, Action) -> Unit)? = null,
    ) = compose.setContent {
        androidx.compose.runtime.CompositionLocalProvider(LocalRowRenders provides { rowBodies++ }) {
            SearchScreen(
                engine, access, onClose = { closed++ },
                contactsDenied = contactsDenied, refresh = refresh, dispatcher = dispatcher,
                onOpenSettings = onOpenSettings, showCursor = showCursor, onHideAction = onHideAction,
                onPicked = { r, a, q ->
                    picked += "${r.id}|$q|closed=$closed"
                    pickedActions += "${a.label}|${a.recentId}"
                },
            )
        }
    }

    private fun type(text: String) {
        compose.onNodeWithTag("search-field").performTextInput(text)
        compose.waitForIdle()
    }

    private fun top(tag: String) = compose.onNodeWithTag(tag).getBoundsInRoot().top.value

    @Test
    fun typing_bed_shows_Tasker_Bedtime_and_one_tap_launches_it() {
        show()
        type("bed")
        compose.onNodeWithText("Tasker: Bedtime").assertIsDisplayed()
        compose.onNodeWithText("Tasker: CreateAlarm").assertDoesNotExist()
        compose.onNodeWithText("Tasker: Bedtime").performClick()
        compose.waitForIdle()
        assertEquals(listOf("shortcut:Bedtime"), launched.toList())
        assertEquals(1, closed)
    }

    @Test
    fun the_keyboard_search_key_launches_the_first_result_and_that_row_carries_the_hint() {
        show(engineOf(app, contact))
        type("e")
        compose.onNodeWithTag("enter-hint", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithTag("enter-hint", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("search-field").performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("app:bed"), launched.toList())
        assertEquals(1, closed)
    }

    @Test
    fun the_keyboard_search_key_does_nothing_without_results() {
        show()
        type("zzz")
        compose.onNodeWithTag("search-field").performImeAction()
        compose.waitForIdle()
        assertTrue(launched.isEmpty())
        assertEquals(0, closed)
        compose.onAllNodesWithTag("enter-hint", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun empty_query_shows_no_results() {
        val queries = CopyOnWriteArrayList<String>()
        val engine = SearchEngine(
            listOf(object : SearchSource {
                override val name = "spy"
                override suspend fun search(query: String): List<SearchResult> {
                    queries += query
                    return tasker.toList()
                }
            }),
        )
        show(engine)
        compose.waitForIdle()
        compose.onNodeWithTag("search-results").onChildren().assertCountEquals(0)
        assertTrue("no source is asked for an empty query", queries.isEmpty())
        type("bed")
        assertEquals(listOf("bed"), queries.toList())
        compose.onNodeWithTag("result-shortcut:Bedtime").assertIsDisplayed()
    }

    @Test
    fun opening_search_refreshes_the_shortcut_index_once() {
        var n = 0
        show(refresh = { n++ })
        type("a")
        type("b")
        assertEquals(1, n)
    }

    @Test
    fun failed_launch_shows_a_visible_error_message() {
        outcome = LaunchOutcome.Failed("Shortcut is no longer available")
        show()
        type("bed")
        compose.onNodeWithText("Tasker: Bedtime").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("search-error").assertTextEquals("Shortcut is no longer available")
        assertEquals(0, closed)
        // The screen stays open and the next keystroke clears the line.
        type("x")
        compose.onNodeWithTag("search-error").assertDoesNotExist()
    }

    @Test
    fun picking_a_result_reports_it_with_the_query() {
        show()
        type("Bed")
        compose.onNodeWithText("Tasker: Bedtime").performClick()
        compose.waitForIdle()
        // Reported once, as typed, before the screen closes.
        assertEquals(listOf("shortcut:Bedtime|Bed|closed=0"), picked.toList())
        assertEquals(1, closed)
    }

    @Test
    fun picking_a_folded_method_reports_the_contact() {
        val gmail = SearchResult(
            "shortcut:gmail", "Gmail: Alex Example", "Shortcuts", Kind.Shortcut, null, action("Open", "shortcut:gmail"),
            appLabel = "Gmail", entryLabel = "Alex Example",
        )
        show(engineOf(contact, gmail))
        type("alex")
        compose.onNodeWithTag("action-contact:1-Gmail", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("shortcut:gmail"), launched.toList())
        assertEquals(listOf("contact:1|alex|closed=0"), picked.toList())
    }

    @Test
    fun picking_a_secondary_action_reports_it_with_the_owner() {
        val keyed = contact.copy(secondary = listOf(action("Call", "call:1").copy(recentId = "contact:1|call|+15550100")))
        show(engineOf(keyed))
        type("alex")
        compose.onNodeWithTag("action-contact:1-Call", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("call:1"), launched.toList())
        assertEquals(listOf("contact:1|alex|closed=0"), picked.toList())
        assertEquals(listOf("Call|contact:1|call|+15550100"), pickedActions.toList())
    }

    private val meet = contact.copy(
        secondary = listOf(
            action("Video call on Meet", "meet:v").copy(recentId = "contact:1|app|x.meet|a@example.com"),
            action("Plain", "plain"),
        ),
    )
    private val hides = CopyOnWriteArrayList<String>()

    @Test
    fun a_long_press_asks_first_and_hides_only_when_confirmed() {
        show(engineOf(meet), onHideAction = { r, a -> hides += "${r.id}|${a.recentId}" })
        type("alex")
        compose.onNodeWithTag("action-contact:1-Video call on Meet", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onNodeWithTag("hide-yes", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("hide-dialog", useUnmergedTree = true).assertTextEquals(
            "\"Video call on Meet\" will no longer show for Alex Example. You can bring it back in Search settings.",
        )
        compose.onNodeWithTag("hide-no").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), hides.toList())
        compose.onNodeWithTag("action-contact:1-Video call on Meet", useUnmergedTree = true).performTouchInput { longClick() }
        compose.onNodeWithTag("hide-yes").performClick()
        compose.waitForIdle()
        assertEquals(listOf("contact:1|contact:1|app|x.meet|a@example.com"), hides.toList())
        assertEquals(emptyList<String>(), launched.toList())
    }

    @Test
    fun a_tap_still_runs_the_action_and_an_action_without_an_id_cannot_be_hidden() {
        show(engineOf(meet), onHideAction = { r, a -> hides += "${r.id}|${a.recentId}" })
        type("alex")
        compose.onNodeWithTag("action-contact:1-Plain", useUnmergedTree = true).performTouchInput { longClick() }
        compose.waitForIdle()
        compose.onAllNodesWithTag("hide-dialog").assertCountEquals(0)
        compose.onNodeWithTag("action-contact:1-Video call on Meet", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("meet:v"), launched.toList())
    }

    @Test
    fun picking_a_primary_reports_no_recent_id() {
        show(engineOf(contact))
        type("alex")
        // The keyboard's Search key runs the top row's primary; a tap on the row's middle can land on an action.
        compose.onNodeWithTag("search-field").performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("contact:1"), launched.toList())
        assertEquals(listOf("contact:1|alex|closed=0"), picked.toList())
        assertEquals(listOf("Open|null"), pickedActions.toList())
    }

    @Test
    fun a_failed_launch_reports_no_pick() {
        outcome = LaunchOutcome.Failed("Shortcut is no longer available")
        show()
        type("bed")
        compose.onNodeWithText("Tasker: Bedtime").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("search-error").assertIsDisplayed()
        assertEquals(emptyList<String>(), picked.toList())
    }

    @Test
    fun contact_row_shows_inline_actions() {
        show(engineOf(contact))
        type("alex")
        compose.onNodeWithText("Alex Example").assertIsDisplayed()
        listOf("Call", "Message", "WhatsApp").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithText("Message").performClick()
        compose.waitForIdle()
        assertEquals(listOf("sms:1"), launched.toList())
        assertEquals(1, closed)
    }

    @Test
    fun only_the_top_result_spells_out_how_to_reach_a_contact() {
        val second = contact.copy(id = "contact:2", label = "Alex Second")
        show(engineOf(contact, second))
        type("alex")
        compose.onNodeWithText("Alex Example").assertIsDisplayed()
        compose.onNodeWithText("Alex Second").assertIsDisplayed()
        // One set of actions, on the first row; the second row is just a name to scan.
        compose.onAllNodesWithTag("action-contact:1-Call", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag("action-contact:2-Call", useUnmergedTree = true).assertCountEquals(0)
    }

    /** A source whose answer depends on the query, and which builds fresh result objects every time, as the real ones do. */
    private fun engineByQuery(answer: (String) -> List<String>) = SearchEngine(
        listOf(object : SearchSource {
            override val name = "fake"
            override suspend fun search(query: String) = answer(query).map { label ->
                SearchResult("app:$label", label, "Apps", Kind.App, null, action("Open", "app:$label"))
            }
        }),
    )

    @Test
    fun a_keystroke_that_leaves_the_results_unchanged_renders_no_row_again() {
        show(engineByQuery { listOf("Alpha one", "Alpha two") })
        type("alp")
        compose.onNodeWithText("Alpha two").assertIsDisplayed()
        val before = rowBodies
        type("h")
        type("a")
        compose.onNodeWithText("Alpha two").assertIsDisplayed()
        assertEquals("rows rendered again though nothing changed", before, rowBodies)
    }

    @Test
    fun a_keystroke_that_adds_one_result_renders_only_that_row() {
        show(engineByQuery { q -> if (q.length > 3) listOf("Alpha one", "Alpha two", "Alpha three") else listOf("Alpha one", "Alpha two") })
        type("alp")
        compose.onNodeWithText("Alpha two").assertIsDisplayed()
        val before = rowBodies
        type("h")
        compose.onNodeWithText("Alpha three").assertIsDisplayed()
        // The new row, plus none of the old ones. (The third row is not first, so the first row's Enter hint is unchanged.)
        assertEquals(before + 1, rowBodies)
    }

    @Test
    fun a_top_contact_whose_shortcuts_arrive_later_is_rendered_once_with_all_its_methods() {
        val person = contact.copy(label = "Alex Example")
        val gmail = SearchResult(
            "shortcut:gmail", "Gmail: Alex Example", "Shortcuts", Kind.Shortcut, null, action("Open", "shortcut:gmail"),
            appLabel = "Gmail", entryLabel = "Alex Example",
        )
        val engine = SearchEngine(
            listOf(
                object : SearchSource {
                    override val name = "contacts"
                    override suspend fun search(query: String) = listOf(person)
                },
                object : SearchSource {
                    // Answers a little later, as a source behind a binder call does.
                    override val name = "shortcuts"
                    override suspend fun search(query: String): List<SearchResult> {
                        kotlinx.coroutines.delay(40)
                        return listOf(gmail)
                    }
                },
            ),
        )
        show(engine)
        compose.waitForIdle()
        val before = rowBodies
        type("alex")
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onNodeWithTag("action-contact:1-Gmail", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("the top row was rendered more than once for one answer", before + 1, rowBodies)
    }

    private fun showWithMenu(engine: SearchEngine, onMenu: (SearchResult) -> Unit) = compose.setContent {
        SearchScreen(
            engine, ShortcutAccess.Granted, onClose = { closed++ },
            dispatcher = Dispatchers.Unconfined, onResultMenu = { r, _ -> onMenu(r) },
        )
    }

    @Test
    fun a_swipe_to_the_right_on_an_app_result_shows_its_menu_and_does_not_launch_it() {
        val menus = mutableListOf<String>()
        showWithMenu(engineOf(app, shortcut("Bedtime"))) { menus += it.id }
        type("bed")
        compose.onNodeWithTag("result-app:bed").performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        compose.waitForIdle()
        assertEquals(listOf("app:bed"), menus)
        // The row's tap did not also fire on release.
        assertTrue(launched.isEmpty())
        assertEquals(0, closed)
    }

    @Test
    fun a_drag_to_the_left_shows_no_menu() {
        val menus = mutableListOf<String>()
        showWithMenu(engineOf(app)) { menus += it.id }
        type("bed")
        compose.onNodeWithTag("result-app:bed").performTouchInput { swipe(Offset(600f, centerY), Offset(100f, centerY)) }
        compose.waitForIdle()
        assertTrue(menus.isEmpty())
    }

    @Test
    fun the_keyboard_search_key_pressed_before_the_new_answer_arrives_launches_the_new_top_result_not_the_old_one() {
        val engine = SearchEngine(
            listOf(object : SearchSource {
                override val name = "fake"
                override suspend fun search(query: String): List<SearchResult> {
                    // The longer query is slower, so its answer lands after the key is pressed.
                    if (query.length > 1) kotlinx.coroutines.delay(60)
                    val label = if (query.length > 1) "Result new" else "Result old"
                    return listOf(SearchResult("app:$label", label, "Apps", Kind.App, null, action("Open", "app:$label")))
                }
            }),
        )
        show(engine)
        type("r")
        compose.onNodeWithText("Result old").assertIsDisplayed()
        // One more letter, and the key at once: the results on screen still answer "r".
        compose.onNodeWithTag("search-field").performTextInput("e")
        compose.onNodeWithTag("search-field").performImeAction()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        assertEquals(listOf("app:Result new"), launched.toList())
    }

    @Test
    fun a_shortcut_or_a_vertical_drag_does_not_show_a_menu() {
        val menus = mutableListOf<String>()
        showWithMenu(engineOf(app, shortcut("Bedtime"))) { menus += it.id }
        type("bed")
        compose.onNodeWithTag("result-shortcut:Bedtime").performTouchInput { swipe(Offset(100f, centerY), Offset(600f, centerY)) }
        compose.onNodeWithTag("result-app:bed").performTouchInput { swipe(Offset(300f, centerY + 80f), Offset(300f, centerY - 80f)) }
        compose.waitForIdle()
        assertTrue(menus.isEmpty())
    }

    @Test
    fun hidden_apps_carry_a_badge() {
        show(engineOf(hidden))
        type("bed")
        compose.onNodeWithText("Hidden").assertIsDisplayed()
    }

    @Test
    fun not_default_home_shows_the_shortcuts_banner() {
        show(access = ShortcutAccess.NotDefaultHome)
        compose.onNodeWithTag("shortcuts-banner").assertIsDisplayed()
    }

    @Test
    fun granted_access_has_no_banner() {
        show()
        compose.onNodeWithTag("shortcuts-banner").assertDoesNotExist()
    }

    @Test
    fun denied_contacts_show_a_grant_prompt() {
        var opened = 0
        show(contactsDenied = true, onOpenSettings = { opened++ })
        compose.onNodeWithTag("contacts-prompt").assertIsDisplayed()
        compose.onNodeWithText("Grant").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun results_show_apps_then_shortcuts_then_contacts() {
        val engine = engineOf(app, shortcut("Bedtime"), contact.copy(label = "Bed Alex"))
        show(engine)
        type("bed")
        val tops = listOf("result-app:bed", "result-shortcut:Bedtime", "result-contact:1").map(::top)
        assertEquals(tops.sorted(), tops)
        assertTrue(tops.toSet().size == 3)
    }

    @Test
    fun swipe_down_closes_search() {
        show()
        type("bed")
        compose.onNodeWithTag("search-results").performTouchInput { swipeDown() }
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun swipe_down_on_the_field_area_closes_search() {
        show()
        compose.onNodeWithTag("search-header").performTouchInput { swipeDown(startY = 0f, endY = 700f) }
        compose.waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun scrolled_results_stay_reachable_and_do_not_close_search() {
        val many = (1..40).map { shortcut("Item$it") }.toTypedArray()
        show(engineOf(*many))
        type("item")
        // A touch drag on a scrollable list never goes idle in Robolectric, so the drag decision
        // is covered on the JVM by PullToCloseTrackerTest; this checks the wiring does not close.
        compose.onNodeWithTag("search-results").performScrollToIndex(30)
        compose.onNodeWithTag("result-shortcut:Item31").assertIsDisplayed()
        compose.waitForIdle()
        assertEquals(0, closed)
    }

    @Test
    fun second_tap_while_a_launch_is_in_flight_does_nothing() {
        val gate = CountDownLatch(1)
        var performs = 0
        val slow = SearchResult(
            "app:slow", "Bedslow", "Apps", Kind.App, null,
            Action("Open", null) { performs++; gate.await(10, TimeUnit.SECONDS); LaunchOutcome.Launched },
            secondary = listOf(action("Call", "call:slow")),
        )
        show(engineOf(slow, shortcut("Bedtime")), dispatcher = Dispatchers.IO)
        type("bed")
        compose.onNodeWithTag("result-app:slow").performClick()
        compose.onNodeWithTag("result-app:slow").performClick()
        compose.onNodeWithTag("result-shortcut:Bedtime").performClick()
        compose.onNodeWithText("Call").performClick()
        gate.countDown()
        compose.waitUntil(5_000) { closed >= 1 }
        compose.waitForIdle()
        assertEquals(1, performs)
        assertEquals(emptyList<String>(), launched.toList())
        assertEquals(1, closed)
    }

    @Test
    fun throwing_action_shows_a_visible_error_and_does_not_crash() {
        val boom = SearchResult(
            "app:boom", "Bedboom", "Apps", Kind.App, null,
            Action("Open", null) { throw SecurityException("secret stack detail that is far too long to show to anybody reading the launcher screen") },
        )
        show(engineOf(boom))
        type("bed")
        compose.onNodeWithTag("result-app:boom").performClick()
        compose.waitForIdle()
        val failed = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.search_launch_failed)
        assertTrue("no raw exception text: $failed", !failed.contains("SecurityException") && !failed.contains("secret stack"))
        compose.onNodeWithTag("search-error").assertTextEquals(failed)
        assertEquals(0, closed)
        // The in-flight flag was cleared: a later launch works.
        outcome = LaunchOutcome.Launched
        type("x")
    }

    @Test
    fun a_short_exception_message_is_never_shown_either() {
        val short = SearchResult(
            "app:short", "Bedshort", "Apps", Kind.App, null,
            Action("Open", null) { throw IllegalStateException("db locked") },
        )
        show(engineOf(short))
        type("bed")
        compose.onNodeWithTag("result-app:short").performClick()
        compose.waitForIdle()
        val failed = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.search_launch_failed)
        compose.onNodeWithTag("search-error").assertTextEquals(failed)
    }

    @Test
    fun a_launch_can_be_retried_after_a_failure() {
        var n = 0
        val flaky = SearchResult(
            "app:flaky", "Bedflaky", "Apps", Kind.App, null,
            Action("Open", null) { if (n++ == 0) throw IllegalStateException("x") else LaunchOutcome.Launched },
        )
        show(engineOf(flaky))
        type("bed")
        compose.onNodeWithTag("result-app:flaky").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("result-app:flaky").performClick()
        compose.waitForIdle()
        assertEquals(2, n)
        assertEquals(1, closed)
    }

    @Test
    fun cancellation_of_the_screen_does_not_report_an_error() {
        val gate = CountDownLatch(1)
        val slow = SearchResult(
            "app:slow", "Bedslow", "Apps", Kind.App, null,
            Action("Open", null) { gate.await(10, TimeUnit.SECONDS); LaunchOutcome.Launched },
        )
        var visible by mutableStateOf(true)
        compose.setContent {
            if (visible) {
                SearchScreen(
                    engineOf(slow), ShortcutAccess.Granted, onClose = { closed++ },
                    dispatcher = Dispatchers.IO,
                )
            }
        }
        type("bed")
        compose.onNodeWithTag("result-app:slow").performClick()
        visible = false
        compose.waitForIdle()
        gate.countDown()
        Thread.sleep(300)
        compose.waitForIdle()
        compose.onNodeWithTag("search-error").assertDoesNotExist()
        assertEquals(0, closed)
    }

    @Test
    fun engine_failure_during_collect_shows_no_results_not_a_crash() {
        val engine = object : SearchEngineFailing() {}.engine
        show(engine)
        type("bed")
        compose.onNodeWithTag("search-results").onChildren().assertCountEquals(0)
        compose.onNodeWithTag("search-field").assertIsDisplayed()
    }

    /** Resolves recents ids from [owned], counting each ask; search answers from the same rows by label. */
    private inner class Recents(private val owned: List<SearchResult>, private val delayMs: Long = 0) : SearchSource {
        override val name = "recents"

        @Volatile var resolves = 0
        override suspend fun search(query: String) = owned.filter { it.label.contains(query, ignoreCase = true) }
        override suspend fun resolve(ids: Set<String>): List<SearchResult> {
            resolves++
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            return owned.filter { it.id in ids }
        }
    }

    private fun recentsEngine(ids: List<String>, vararg sources: SearchSource, budgetMs: Long? = null) =
        if (budgetMs == null) SearchEngine(sources.toList(), recents = { ids })
        else SearchEngine(sources.toList(), recents = { ids }, recentsSettleMs = budgetMs)

    private fun resultIdsOnScreen(vararg ids: String) =
        ids.filter { compose.onAllNodesWithTag("result-$it").fetchSemanticsNodes().isNotEmpty() }.sortedBy { top("result-$it") }

    @Test
    fun empty_search_shows_recents_and_enter_opens_the_first() {
        show(recentsEngine(listOf("shortcut:Bedtime", "app:bed"), Recents(listOf(app, shortcut("Bedtime")))))
        compose.waitForIdle()
        assertEquals(listOf("shortcut:Bedtime", "app:bed"), resultIdsOnScreen("shortcut:Bedtime", "app:bed"))
        compose.onAllNodesWithTag("enter-hint", useUnmergedTree = true).assertCountEquals(1)
        compose.onNodeWithTag("search-field").performImeAction()
        compose.waitForIdle()
        assertEquals(listOf("shortcut:Bedtime"), launched.toList())
        // A normal pick, with nothing typed.
        assertEquals(listOf("shortcut:Bedtime||closed=0"), picked.toList())
        assertEquals(1, closed)
    }

    @Test
    fun typing_replaces_recents_and_clearing_brings_them_back() {
        show(recentsEngine(listOf("app:bed", "contact:1"), Recents(listOf(app, contact, shortcut("Work")))))
        compose.waitForIdle()
        assertEquals(listOf("app:bed", "contact:1"), resultIdsOnScreen("app:bed", "contact:1", "shortcut:Work"))
        type("work")
        assertEquals(listOf("shortcut:Work"), resultIdsOnScreen("app:bed", "contact:1", "shortcut:Work"))
        compose.onNodeWithTag("search-field").performTextClearance()
        compose.waitForIdle()
        assertEquals(listOf("app:bed", "contact:1"), resultIdsOnScreen("app:bed", "contact:1", "shortcut:Work"))
    }

    @Test
    fun clearing_the_field_again_resolves_nothing() {
        val source = Recents(listOf(app, shortcut("Bedtime")))
        show(recentsEngine(listOf("app:bed", "shortcut:Bedtime"), source))
        compose.waitForIdle()
        assertEquals(1, source.resolves)
        repeat(2) {
            type("bed")
            compose.onNodeWithTag("search-field").performTextClearance()
            compose.waitForIdle()
        }
        assertEquals(listOf("app:bed", "shortcut:Bedtime"), resultIdsOnScreen("app:bed", "shortcut:Bedtime"))
        assertEquals(1, source.resolves)
    }

    @Test
    fun the_list_does_not_reorder_once_shown() {
        // The newest pick belongs to a source too slow for the budget: it is left out, never slotted in on top later.
        val late = Recents(listOf(shortcut("Bedtime")), delayMs = 1_000)
        show(recentsEngine(listOf("shortcut:Bedtime", "app:bed", "app:hid"), late, Recents(listOf(app, hidden)), budgetMs = 250))
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        assertEquals(listOf("app:bed", "app:hid"), resultIdsOnScreen("shortcut:Bedtime", "app:bed", "app:hid"))
        val before = rowBodies
        compose.mainClock.advanceTimeBy(2_000)
        compose.waitForIdle()
        assertEquals(listOf("app:bed", "app:hid"), resultIdsOnScreen("shortcut:Bedtime", "app:bed", "app:hid"))
        assertEquals("a shown row rendered again", before, rowBodies)
    }

    @Test
    fun every_recents_row_is_a_plain_row() {
        val second = contact.copy(id = "contact:2", label = "Alex Second")
        show(recentsEngine(listOf("contact:1", "contact:2"), Recents(listOf(contact, second))))
        compose.waitForIdle()
        compose.onNodeWithTag("result-contact:1").assertIsDisplayed()
        // Even the first row, which carries the Enter hint, lists no ways of reaching the contact.
        compose.onAllNodesWithTag("actions-contact:1", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("action-contact:1-Call", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("result-contact:1").performClick()
        compose.waitForIdle()
        assertEquals(listOf("contact:1"), launched.toList())
    }

    private fun event(
        id: Int,
        title: String,
        prefix: String = "Personal",
        location: String? = null,
        recurring: Boolean = false,
    ) = SearchResult(
        "event:$id", title, "Events", Kind.Event, null, action("Open", "event:$id"),
        secondary = listOfNotNull(location?.let { action(it, "loc:$id") }),
        prefix = prefix, detail = "Tue, Jan 13 \u00b7 9:30 AM", recurring = recurring,
    )

    private fun heightOf(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().let { it.bottom - it.top }

    private val address = "Osteria Rosa, 120 Main St"

    @Test
    fun the_top_event_shows_its_detail_a_repeat_mark_and_its_location() {
        show(engineOf(event(1, "Dentist", location = address, recurring = true), event(2, "Dentist visit", prefix = "Work")))
        type("dent")
        compose.onNodeWithTag("calendar-event:1", useUnmergedTree = true).assertTextEquals("Personal")
        compose.onNodeWithTag("detail-event:1", useUnmergedTree = true).assertTextEquals("Tue, Jan 13 \u00b7 9:30 AM")
        val repeats = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.search_event_repeats)
        compose.onNodeWithTag("repeats-event:1", useUnmergedTree = true).assertContentDescriptionEquals(repeats)
        compose.onNodeWithTag("action-event:1-$address", useUnmergedTree = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("loc:1"), launched.toList())
    }

    @Test
    fun a_long_calendar_name_gives_way_so_the_date_and_repeat_mark_stay_visible() {
        val email = "jane.doe.with.a.very.long.address.example@gmail.com and more"
        show(engineOf(event(1, "Dentist", prefix = email, recurring = true)))
        type("dent")
        val rootWidth = compose.onRoot().getBoundsInRoot().let { it.right - it.left }
        for (tag in listOf("detail-event:1", "repeats-event:1")) {
            compose.onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
            assertTrue("$tag runs off the row", compose.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot().right <= rootWidth)
        }
        // The date is whole: its text was not cut to an ellipsis.
        compose.onNodeWithTag("detail-event:1", useUnmergedTree = true).assertTextEquals("Tue, Jan 13 \u00b7 9:30 AM")
    }

    @Test
    fun a_top_event_that_does_not_repeat_has_no_repeat_mark() {
        show(engineOf(event(1, "Dentist")))
        type("dent")
        compose.onAllNodesWithTag("repeats-event:1", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun other_event_rows_are_one_line_with_the_calendar_before_the_title() {
        show(engineOf(event(1, "Dentist", location = address, recurring = true), event(2, "Dentist visit", prefix = "Work", location = "Room 4", recurring = true)))
        type("dent")
        compose.onAllNodesWithTag("detail-event:2", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("repeats-event:2", useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag("actions-event:2", useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithText("Work: Dentist visit", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun a_long_title_or_address_stays_on_one_line() {
        val longTitle = "Quarterly planning offsite with the whole leadership team and partners ".repeat(5)
        val longAddress = "Building 4, 1200 Corporate Boulevard, Suite 300, Springfield, Somewhere County, 80000, United States"
        show(
            engineOf(
                event(1, "Short dinner", location = address),
                event(2, "Short dinner two"),
                event(3, "Short dinner $longTitle"),
                event(4, "Long dinner", location = longAddress),
            ),
        )
        type("short")
        val shortTitleRow = heightOf("result-event:2")
        assertEquals(shortTitleRow.value, heightOf("result-event:3").value, 0.5f)
        val shortAction = heightOf("action-event:1-$address")
        compose.onNodeWithTag("search-field").performTextClearance()
        type("long dinner")
        assertEquals(shortAction.value, heightOf("action-event:4-$longAddress").value, 0.5f)
    }

    @Test
    fun search_results_with_events() {
        show(engineOf(event(1, "Dinner with Sam", location = address, recurring = true), event(2, "Our dinner at Grandma's", prefix = "Family"), event(3, "Team dinner", prefix = "Work")), showCursor = false)
        type("dinner")
        compose.onRoot().captureRoboImage("src/test/snapshots/search_results_events.png")
    }

    @Test
    fun search_results() {
        show(engineOf(app, shortcut("Bedtime"), contact.copy(label = "Bed Alex"), hidden), showCursor = false)
        type("bed")
        compose.onRoot().captureRoboImage("src/test/snapshots/search_results.png")
    }
}

// An engine whose flow throws on collect, to prove the screen survives a broken engine.
private open class SearchEngineFailing {
    val engine = object : SearchEngine(emptyList()) {
        override fun results(query: String) = kotlinx.coroutines.flow.flow<List<SearchResult>> {
            if (query.isNotEmpty()) throw IllegalStateException("engine broke")
            emit(emptyList())
        }
    }
}
