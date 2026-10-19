package com.lerchenflo.relisto.utilities

/** willhaben stores listing descriptions as simple HTML (line breaks as `<br>`); the editor uses plain text. */
object HtmlText {

    private val LINE_BREAK = Regex("""<\s*br\s*/?\s*>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]+>""")
    private val ENTITIES = mapOf(
        "&nbsp;" to " ",
        "&lt;" to "<",
        "&gt;" to ">",
        "&quot;" to "\"",
        "&#39;" to "'",
        "&apos;" to "'",
    )

    fun toPlainText(html: String): String {
        var text = html.replace(LINE_BREAK, "\n").replace(TAG, "")
        ENTITIES.forEach { (entity, character) -> text = text.replace(entity, character) }
        // &amp; last, so "&amp;lt;" stays the literal text "&lt;"
        return text.replace("&amp;", "&").trim()
    }

    fun fromPlainText(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br />")
}
