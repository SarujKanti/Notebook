package com.skd.notebook.util

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan

private const val SNIPPET_LEAD = 40
private const val SNIPPET_TRIGGER = 60

/** Marks every case-insensitive occurrence of [query] in [text] with a tinted background and bold. */
fun highlightMatches(text: String, query: String, color: Int): CharSequence {
    if (query.isEmpty()) return text
    val spannable = SpannableString(text)
    var from = text.indexOf(query, ignoreCase = true)
    while (from >= 0) {
        val to = from + query.length
        spannable.setSpan(BackgroundColorSpan(color), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannable.setSpan(StyleSpan(Typeface.BOLD), from, to, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        from = text.indexOf(query, to, ignoreCase = true)
    }
    return spannable
}

/**
 * Long notes are cut to a few lines on the card, so a match deep in the text would be
 * invisible. Start the preview shortly before the first match in that case.
 */
fun snippetAroundMatch(text: String, query: String): String {
    if (query.isEmpty()) return text
    val at = text.indexOf(query, ignoreCase = true)
    if (at <= SNIPPET_TRIGGER) return text
    return "…" + text.substring(at - SNIPPET_LEAD).trimStart()
}

/** Escapes LIKE wildcards so typing % or _ searches for those characters literally. */
fun escapeLike(query: String) =
    query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
