package com.noam.kidtube.core.store

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogStoreTest {

    private val root: File = Files.createTempDirectory("catalog").toFile()
    private val store = CatalogStore(FileStore(root))
    private val channelId = ChannelId("UC_abc")

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun video(id: String, published: Long) =
        VideoRef(VideoId(id), channelId, "title $id", null, 100L, published)

    @Test
    fun `an unknown channel has an empty catalog`() {
        assertEquals(emptyList(), store.load(ChannelId("UC_nothing")))
    }

    @Test
    fun `a saved catalog reads back in order`() {
        store.save(channelId, listOf(video("v1", 300), video("v2", 200)))
        assertEquals(listOf("v1", "v2"), store.load(channelId).map { it.id.value })
    }

    @Test
    fun `each channel gets its own file`() {
        store.save(channelId, listOf(video("v1", 1)))
        store.save(ChannelId("UC_xyz"), listOf(video("v9", 1)))
        assertTrue(File(root, "catalog/UC_abc.json").isFile)
        assertTrue(File(root, "catalog/UC_xyz.json").isFile)
        assertEquals(listOf(ChannelId("UC_abc"), ChannelId("UC_xyz")), store.channelIds())
    }

    @Test
    fun `merge appends only unseen ids and reports the count`() {
        store.save(channelId, listOf(video("v1", 300), video("v2", 200)))
        val added = store.merge(channelId, listOf(video("v0", 400), video("v1", 300)))
        assertEquals(1, added)
        assertEquals(listOf("v0", "v1", "v2"), store.load(channelId).map { it.id.value })
    }

    @Test
    fun `merge keeps newest first so a fresh rss entry lands at the top`() {
        store.save(channelId, listOf(video("old", 100)))
        store.merge(channelId, listOf(video("new", 900)))
        assertEquals(listOf("new", "old"), store.load(channelId).map { it.id.value })
    }

    @Test
    fun `merging nothing new reports zero and rewrites nothing`() {
        store.save(channelId, listOf(video("v1", 300)))
        assertEquals(0, store.merge(channelId, listOf(video("v1", 300))))
        assertEquals(1, store.load(channelId).size)
    }

    @Test
    fun `merge into an empty catalog seeds it`() {
        assertEquals(2, store.merge(channelId, listOf(video("v1", 200), video("v2", 100))))
    }

    @Test
    fun `entries without a publish date sort last rather than crashing`() {
        store.save(channelId, listOf(video("dated", 500)))
        store.merge(channelId, listOf(VideoRef(VideoId("undated"), channelId, "t", null, 10L, null)))
        assertEquals(listOf("dated", "undated"), store.load(channelId).map { it.id.value })
    }

    @Test
    fun `delete removes the catalog file`() {
        store.save(channelId, listOf(video("v1", 1)))
        store.delete(channelId)
        assertEquals(emptyList(), store.load(channelId))
        assertEquals(emptyList(), store.channelIds())
    }

    @Test
    fun `a channel id containing a path separator is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            store.save(ChannelId("../../etc/passwd"), listOf(video("v1", 1)))
        }
    }
}
