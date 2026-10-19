package com.noam.kidtube.core.store

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cache of titles already translated.
 *
 * Translating is the expensive half of showing a Hebrew title, and a catalog of several
 * hundred videos changes rarely, so each title is translated once and the answer is kept.
 * That also means the child's screen can build its titles synchronously - no waiting on a
 * translator while a grid is being laid out.
 */
class TranslationStoreTest {

    private val root: File = Files.createTempDirectory("translations").toFile()
    private val store = TranslationStore(FileStore(root))

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    @Test
    fun `a fresh store knows nothing`() {
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `what was put in comes back out`() {
        store.putAll(mapOf("Baby Shark" to "בייבי שארק"))

        assertEquals("בייבי שארק", store.all()["Baby Shark"])
    }

    @Test
    fun `putting more keeps what was already there`() {
        store.putAll(mapOf("one" to "אחת"))
        store.putAll(mapOf("two" to "שתיים"))

        assertEquals(setOf("one", "two"), store.all().keys)
    }

    @Test
    fun `missing names only what has not been translated`() {
        store.putAll(mapOf("known" to "ידוע"))

        assertEquals(listOf("fresh"), store.missing(listOf("known", "fresh")))
    }

    @Test
    fun `missing does not repeat a title that appears twice`() {
        // The same title really does appear more than once across channels, and
        // translating it twice costs twice.
        assertEquals(listOf("same"), store.missing(listOf("same", "same")))
    }

    @Test
    fun `a blank title is never worth translating`() {
        assertTrue(store.missing(listOf("", "   ")).isEmpty())
    }

    @Test
    fun `a translation equal to its original is still remembered`() {
        // Otherwise a title the translator leaves alone - a bare number, a name - is
        // offered to it again on every pass, forever.
        store.putAll(mapOf("Pokemon" to "Pokemon"))

        assertTrue(store.missing(listOf("Pokemon")).isEmpty())
    }

    @Test
    fun `the cache survives a restart`() {
        store.putAll(mapOf("Baby Shark" to "בייבי"))

        assertEquals("בייבי", TranslationStore(FileStore(root)).all()["Baby Shark"])
    }

    @Test
    fun `clearing forgets everything, so turning the feature off really turns it off`() {
        store.putAll(mapOf("Baby Shark" to "בייבי"))

        store.clear()

        assertTrue(store.all().isEmpty())
    }
}
