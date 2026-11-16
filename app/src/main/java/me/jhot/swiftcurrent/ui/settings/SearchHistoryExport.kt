package me.jhot.swiftcurrent.ui.settings

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import me.jhot.swiftcurrent.core.db.SelectionRow

/** Search history as a JSON array, one object per pick, for the owner to read or analyse; nothing reads it back. */
object SearchHistoryExport {
    fun toJson(rows: List<SelectionRow>): String = JsonArray(
        rows.map { r ->
            buildJsonObject {
                put("id", r.id)
                put("resultId", r.resultId)
                put("kind", r.kind)
                put("query", JsonPrimitive(r.query))
                put("source", r.source)
                put("at", r.at)
                put("actionId", JsonPrimitive(r.actionId))
            }
        },
    ).toString()
}
