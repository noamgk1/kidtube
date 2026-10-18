package com.noam.kidtube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BidiTextTest {

    private val fsi = BidiText.FIRST_STRONG_ISOLATE
    private val pdi = BidiText.POP_DIRECTIONAL_ISOLATE

    @Test
    fun `an English title is wrapped in an isolate`() {
        // Without this, the bidi algorithm reorders the punctuation at the edges of an
        // English title sitting inside the Hebrew layout: "Songs | Kids" came out with
        // the bar and the trailing word on the wrong side.
        assertEquals(
            fsi.toString() + "Halloween Night | Kids Songs" + pdi,
            BidiText.isolate("Halloween Night | Kids Songs"),
        )
    }

    @Test
    fun `a Hebrew title is wrapped too, and renders the same as it always did`() {
        val hebrew = "שירים לילדים"
        assertEquals(fsi.toString() + hebrew + pdi, BidiText.isolate(hebrew))
    }

    @Test
    fun `blank text is left alone`() {
        // Wrapping nothing would put two invisible characters into an empty line and give
        // it a height it should not have.
        assertEquals("", BidiText.isolate(""))
        assertEquals("   ", BidiText.isolate("   "))
    }

    @Test
    fun `isolating twice does not nest`() {
        val once = BidiText.isolate("Baby Shark")
        assertEquals(once, BidiText.isolate(once))
    }

    @Test
    fun `the visible characters are untouched`() {
        val title = "Top 20 שירים! (2026)"
        val isolated = BidiText.isolate(title)

        assertEquals(title, isolated.filterNot { it == fsi || it == pdi })
        assertTrue(isolated.length == title.length + 2)
    }
}
