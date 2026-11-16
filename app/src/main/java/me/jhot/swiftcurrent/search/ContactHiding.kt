package me.jhot.swiftcurrent.search

/**
 * Leaves out the ways to reach a contact that the owner hid. A way is known by its contact's result id and its
 * action's recent id, the same id a pick of it is remembered by: a contact key for a phone, email or connected-app
 * row, a shortcut id for a shortcut folded onto the contact. The contact itself and its primary action stay.
 */
object ContactHiding {
    fun key(contact: String, action: String): String = "$contact|$action"

    fun apply(results: List<SearchResult>, hidden: Set<String>): List<SearchResult> {
        if (hidden.isEmpty()) return results
        return results.map { r ->
            if (r.kind != Kind.Contact || r.secondary.isEmpty()) r
            else r.copy(secondary = r.secondary.filterNot { a -> a.recentId != null && key(r.id, a.recentId) in hidden })
        }
    }
}
