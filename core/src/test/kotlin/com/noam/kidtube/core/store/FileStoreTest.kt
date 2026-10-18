package com.noam.kidtube.core.store

import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Serializable
private data class Sample(val name: String, val count: Int)

class FileStoreTest {

    private val root: File = Files.createTempDirectory("filestore").toFile()
    private val corruptions = mutableListOf<String>()
    private val store = FileStore(root) { path, _ -> corruptions += path }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `a written value reads back`() {
        store.write("a.json", Sample.serializer(), Sample("x", 3))
        assertEquals(Sample("x", 3), store.read("a.json", Sample.serializer()) { Sample("", 0) })
    }

    @Test
    fun `a missing file yields the default and is not reported as corrupt`() {
        assertEquals(Sample("d", 0), store.read("nope.json", Sample.serializer()) { Sample("d", 0) })
        assertTrue(corruptions.isEmpty())
    }

    @Test
    fun `corrupt json yields the default and is reported for the admin screen`() {
        File(root, "bad.json").writeText("{ this is not json")
        assertEquals(Sample("d", 0), store.read("bad.json", Sample.serializer()) { Sample("d", 0) })
        assertEquals(listOf("bad.json"), corruptions)
    }

    @Test
    fun `a write leaves no temp file behind`() {
        store.write("a.json", Sample.serializer(), Sample("x", 1))
        val leftovers = root.listFiles()?.filter { it.name.endsWith(".tmp") } ?: emptyList()
        assertTrue(leftovers.isEmpty(), "found $leftovers")
    }

    @Test
    fun `a stray temp file from an interrupted write does not disturb the real file`() {
        store.write("a.json", Sample.serializer(), Sample("good", 1))
        File(root, "a.json.tmp").writeText("{ half-written garbage")

        assertEquals(Sample("good", 1), store.read("a.json", Sample.serializer()) { Sample("", 0) })
        assertTrue(corruptions.isEmpty())
    }

    @Test
    fun `nested paths are created on demand`() {
        store.write("catalog/UC_abc.json", Sample.serializer(), Sample("c", 9))
        assertTrue(File(root, "catalog/UC_abc.json").isFile)
        assertEquals(listOf("UC_abc.json"), store.listNames("catalog"))
    }

    @Test
    fun `listNames on a missing directory is empty rather than an error`() {
        assertEquals(emptyList(), store.listNames("not-there"))
    }

    @Test
    fun `lines append in order and can be replaced wholesale`() {
        store.appendLine("log.jsonl", "one")
        store.appendLine("log.jsonl", "two")
        assertEquals(listOf("one", "two"), store.readLines("log.jsonl"))

        store.replaceLines("log.jsonl", listOf("three"))
        assertEquals(listOf("three"), store.readLines("log.jsonl"))
    }

    @Test
    fun `blank lines are skipped when reading a log`() {
        File(root, "log.jsonl").writeText("one\n\ntwo\n\n")
        assertEquals(listOf("one", "two"), store.readLines("log.jsonl"))
    }

    @Test
    fun `delete removes a file and reports absence afterwards`() {
        store.write("a.json", Sample.serializer(), Sample("x", 1))
        assertTrue(store.exists("a.json"))
        store.delete("a.json")
        assertFalse(store.exists("a.json"))
    }
}
