package me.jhot.swiftcurrent.search.sources

private val PHONE_TAIL = Regex("[\\s0-9+()\\-]+$")

/**
 * "Signal Video" for a row whose own text reads "Signal Video Call +1 555...": the app that owns the row,
 * then the one word of its text that says what the row does. Falls back to the app name alone, because an
 * app is free to put only a number, or nothing, in the row's text.
 */
fun connectedActionLabel(appName: String, text: String?, data: String): String {
    // Meet words its row "Video call on Meet": already an action and the app, and shorter would lose the "on".
    text?.trim()?.let { whole ->
        if (whole.any(Char::isDigit).not() && Regex("\\b${Regex.escape(appName)}\\b", RegexOption.IGNORE_CASE).containsMatchIn(whole) &&
            whole.split(Regex("\\s+")).size > 1
        ) return whole
    }
    var rest = text.orEmpty()
    // DATA1 only as a whole token: a short value must not eat letters out of a word.
    if (data.isNotBlank()) rest = rest.split(' ').filter { it != data }.joinToString(" ")
    rest = Regex("\\b${Regex.escape(appName)}\\b", RegexOption.IGNORE_CASE).replace(rest, " ")
    // The number is written however the app likes (and not always as DATA1), so strip any trailing number.
    rest = PHONE_TAIL.replace(rest, "").trim()
    val words = rest.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val word = if (words.size > 1 && words.last().equals("call", ignoreCase = true)) words.dropLast(1) else words
    return if (word.isEmpty()) appName else "$appName ${word.joinToString(" ")}"
}

/** The number a row is for, as the row words it ("+1 (555) 010-0123"), else its DATA1: what tells two numbers of one app apart. */
fun connectedActionNumber(text: String?, data: String): String =
    text?.let { PHONE_TAIL.find(it)?.value?.trim() }?.takeIf { it.any(Char::isDigit) } ?: dashed(data)

/** Ten bare digits are a US number written without punctuation (Meet stores it so); anything else is left as written. */
private fun dashed(data: String): String =
    if (data.length == 10 && data.all(Char::isDigit)) "${data.take(3)}-${data.substring(3, 6)}-${data.drop(6)}" else data

/**
 * Buttons of one app that read alike (Meet's video and audio rows both say "Meet") are told apart by the part of
 * their mimetype that differs ("...phone.meet", "...phone.meet.audio" gives "Meet" and "Meet Audio").
 */
fun distinguishLabels(labels: List<String>, mimetypes: List<String>): List<String> {
    val out = labels.toMutableList()
    for (same in labels.indices.groupBy { labels[it] }.values) {
        if (same.size < 2) continue
        val prefix = commonSegments(same.map { mimetypes[it] })
        for (i in same) {
            val tail = mimetypes[i].removePrefix(prefix).trim('.')
            if (tail.isNotEmpty()) out[i] = "${labels[i]} ${tail.replace('.', ' ').replaceFirstChar(Char::uppercase)}"
        }
    }
    return out
}

/** The longest run of leading dot-separated segments all of [types] share. */
private fun commonSegments(types: List<String>): String {
    val split = types.map { it.split('.') }
    val shared = split.first().indices.takeWhile { i -> split.all { it.size > i && it[i] == split.first()[i] } }
    return split.first().take(shared.size).joinToString(".")
}
