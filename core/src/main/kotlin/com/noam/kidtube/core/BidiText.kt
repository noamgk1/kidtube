package com.noam.kidtube.core

/**
 * Keeps a foreign-language title readable inside the Hebrew layout.
 *
 * Almost every video title here is English while the app around it is right-to-left, and
 * the bidi algorithm resolves the two together: punctuation at the edges of the English
 * run — the bars, brackets and exclamation marks that YouTube titles are full of — gets
 * reordered, and the title renders with its pieces in the wrong places even though every
 * character is correct.
 *
 * The fix is isolation, not direction. Telling the text to resolve its own direction
 * (Compose's `TextDirection.Content`, the web's `dir="auto"`) would also hand it its own
 * *alignment*, so an English title would left-align while the Hebrew one beside it
 * right-aligned and a row of cards would look broken. Wrapping the run in a first-strong
 * isolate instead is the web's `<bdi>`: the run resolves internally, and the layout keeps
 * deciding where it sits.
 */
object BidiText {

    /** U+2068. Opens a run whose direction comes from its own first strong character. */
    const val FIRST_STRONG_ISOLATE: Char = '⁨'

    /** U+2069. Closes it. */
    const val POP_DIRECTIONAL_ISOLATE: Char = '⁩'

    /**
     * Wraps [text] so it renders as its own directional run.
     *
     * Blank text is returned unchanged — two invisible characters would still give an
     * empty line a height — and text that is already isolated is left as it is.
     */
    fun isolate(text: String): String = when {
        text.isBlank() -> text
        text.first() == FIRST_STRONG_ISOLATE && text.last() == POP_DIRECTIONAL_ISOLATE -> text
        else -> FIRST_STRONG_ISOLATE + text + POP_DIRECTIONAL_ISOLATE
    }
}
