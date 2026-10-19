package com.noam.kidtube.app.kid

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.TranslationStore
import com.noam.kidtube.core.store.WhitelistStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KidGridLoaderTest {

    private val root: File = Files.createTempDirectory("grid").toFile()
    private val files = FileStore(root)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val loader = KidGridLoader(whitelistStore, catalogStore, TranslationStore(files))

    private val kids = ChannelId("UC_kids")
    private val science = ChannelId("UC_sci")

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun video(channel: ChannelId, id: String, published: Long) =
        VideoRef(VideoId(id), channel, "t " + id, null, 60L, published)

    private fun seed() {
        whitelistStore.addChannel(Channel(kids, "Kids", null, "f1", 1L))
        whitelistStore.addChannel(Channel(science, "Science", null, "f2", 1L))
        catalogStore.save(kids, listOf(video(kids, "k1", 900), video(kids, "k2", 700)))
        catalogStore.save(science, listOf(video(science, "s1", 800)))
    }

    @Test
    fun `with nothing approved the grid is empty`() {
        val model = loader.load(selected = null)
        assertTrue(model.channels.isEmpty())
        assertTrue(model.videos.isEmpty())
    }

    @Test
    fun `with no channel selected every approved video appears newest first`() {
        seed()
        assertEquals(listOf("k1", "s1", "k2"), loader.load(null).videos.map { it.id.value })
    }

    @Test
    fun `selecting a channel filters to that channel`() {
        seed()
        val model = loader.load(kids)
        assertEquals(listOf("k1", "k2"), model.videos.map { it.id.value })
        assertEquals(kids, model.selectedChannelId)
    }

    @Test
    fun `individually approved videos appear alongside channel videos`() {
        seed()
        whitelistStore.addSingleVideo(video(ChannelId("UC_other"), "single1", 950))
        assertEquals(
            listOf("single1", "k1", "s1", "k2"),
            loader.load(null).videos.map { it.id.value },
        )
    }

    @Test
    fun `a blocked video never reaches the grid`() {
        seed()
        whitelistStore.block(VideoId("k1"))
        assertEquals(listOf("s1", "k2"), loader.load(null).videos.map { it.id.value })
        assertEquals(listOf("k2"), loader.load(kids).videos.map { it.id.value })
    }

    @Test
    fun `blocking an individually approved video also removes it`() {
        whitelistStore.addSingleVideo(video(kids, "single1", 950))
        whitelistStore.block(VideoId("single1"))
        assertTrue(loader.load(null).videos.isEmpty())
    }

    @Test
    fun `a channel with no videos yet is hidden from the strip`() {
        whitelistStore.addChannel(Channel(kids, "Kids", null, "f1", 1L))
        assertTrue(
            loader.load(null).channels.isEmpty(),
            "an empty channel avatar would lead a child nowhere",
        )
    }

    @Test
    fun `a channel reappears in the strip as soon as its first page lands`() {
        whitelistStore.addChannel(Channel(kids, "Kids", null, "f1", 1L))
        catalogStore.save(kids, listOf(video(kids, "k1", 900)))
        assertEquals(listOf(kids), loader.load(null).channels.map { it.id })
    }

    @Test
    fun `a catalog left over from a removed channel does not leak back in`() {
        seed()
        whitelistStore.removeChannel(kids)
        assertEquals(listOf("s1"), loader.load(null).videos.map { it.id.value })
    }

    @Test
    fun `undated videos sort last rather than jumping to the top`() {
        whitelistStore.addChannel(Channel(kids, "Kids", null, "f1", 1L))
        catalogStore.save(
            kids,
            listOf(
                VideoRef(VideoId("undated"), kids, "t", null, null, null),
                video(kids, "dated", 500),
            ),
        )
        assertEquals(listOf("dated", "undated"), loader.load(null).videos.map { it.id.value })
    }

    @Test
    fun `channels appear in the strip sorted by title`() {
        seed()
        assertEquals(listOf("Kids", "Science"), loader.load(null).channels.map { it.title })
    }

    // What the child reads. YouTube supplies a creator's own Hebrew title where one
    // exists; for everything else the on-device translation is swapped in here, at the
    // single place the child's screens get their content from.

    @Test
    fun `a translated title is what the child sees`() {
        val hebrew = "בייבי שארק"
        val translations = TranslationStore(files)
        translations.putAll(mapOf("t k1" to hebrew))
        whitelistStore.addChannel(Channel(kids, "Kids", null, "f1", 1L))
        catalogStore.save(kids, listOf(video(kids, "k1", 900)))

        val snapshot = KidGridLoader(whitelistStore, catalogStore, translations).snapshot()

        assertEquals(hebrew, snapshot.videos.single().title)
    }

    @Test
    fun `an untranslated title keeps the original rather than going blank`() {
        whitelistStore.addChannel(Channel(kids, "Kids", null, "f1", 1L))
        catalogStore.save(kids, listOf(video(kids, "k1", 900)))

        assertEquals("t k1", loader.snapshot().videos.single().title)
    }
}
