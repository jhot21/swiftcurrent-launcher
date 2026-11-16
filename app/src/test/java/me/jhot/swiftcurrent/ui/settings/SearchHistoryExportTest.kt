package me.jhot.swiftcurrent.ui.settings

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.jhot.swiftcurrent.core.db.SelectionRow
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryExportTest {
    @Test
    fun round_trips_rows() {
        val rows = listOf(
            SelectionRow(id = 1, resultId = "app:pkg.a/.Main", kind = "app", query = "al", source = "search", at = 1_700_000_000_000),
            // A launch outside search has no query; it must come out as null, not the text "null".
            SelectionRow(id = 2, resultId = "app:pkg.b/.Main", kind = "app", query = null, source = "launcher", at = 1_700_000_000_500),
            SelectionRow(id = 3, resultId = "contact:7", kind = "contact", query = "say \"hi\"\n", source = "search", at = 0),
            // The specific action picked inside a result, absent for plain launches.
            SelectionRow(id = 4, resultId = "app:pkg.a/.Main", kind = "app", query = "al", source = "search", at = 5, actionId = "shortcut:compose"),
        )
        val back = Json.parseToJsonElement(SearchHistoryExport.toJson(rows)).jsonArray.map { e ->
            val o = e.jsonObject
            SelectionRow(
                id = o.getValue("id").jsonPrimitive.long,
                resultId = o.getValue("resultId").jsonPrimitive.content,
                kind = o.getValue("kind").jsonPrimitive.content,
                query = o.getValue("query").let { if (it is JsonNull) null else it.jsonPrimitive.content },
                source = o.getValue("source").jsonPrimitive.content,
                at = o.getValue("at").jsonPrimitive.long,
                actionId = o.getValue("actionId").let { if (it is JsonNull) null else it.jsonPrimitive.content },
            )
        }
        assertEquals(rows, back)
    }

    @Test
    fun no_rows_is_an_empty_array() {
        assertEquals(0, Json.parseToJsonElement(SearchHistoryExport.toJson(emptyList())).jsonArray.size)
    }
}
