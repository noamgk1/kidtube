package com.noam.kidtube.core

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TitleTranslatorTest {

    @Test
    fun `None reports that no translator is installed`() {
        assertFalse(TitleTranslator.None.isInstalled)
    }

    @Test
    fun `an implementation is installed unless it says otherwise`() {
        val anyTranslator = object : TitleTranslator {
            override suspend fun prepare(): Boolean = true
            override suspend fun translate(titles: List<String>): Map<String, String> = emptyMap()
        }
        assertTrue(anyTranslator.isInstalled)
    }

    @Test
    fun `None never claims to be ready`() = runTest {
        // TitleTranslationPass stops on a false prepare, which is what keeps it from
        // walking the whole catalog for nothing on every sync.
        assertFalse(TitleTranslator.None.prepare())
    }

    @Test
    fun `None translates nothing, so every title keeps its original`() = runTest {
        val titles = listOf("Peppa Pig Full Episodes", "מתכונים לילדים")

        val answers = TitleTranslator.None.translate(titles)

        // Absent rather than echoed back: the grid keeps the original for a title the
        // cache has no answer for, and storing the original as a "translation" would
        // make a later real translator skip it.
        assertEquals(emptyMap(), answers)
    }
}
