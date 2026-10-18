package com.noam.kidtube.app.admin

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.WhitelistStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CatalogPresenterTest {

    private val root: File = Files.createTempDirectory("catalogscreen").toFile()
    private val files = FileStore(root)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val presenter = CatalogPresenter(whitelistStore, catalogStore)

    private val channelId = ChannelId("UC_abc")

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun seed() {
        whitelistStore.addChannel(Channel(channelId, "Kids", null, "f", 1L))
        catalogStore.save(
            channelId,
            listOf(
                VideoRef(VideoId("v1"), channelId, "First", null, 60L, 900L),
                VideoRef(VideoId("v2"), channelId, "Second", null, 90L, 800L),
            ),
        )
    }

    @Test
    fun `every catalogued video is listed, blocked ones included`() {
        seed()
        whitelistStore.block(VideoId("v1"))

        val rows = presenter.rows(channelId)
        assertEquals(listOf("v1", "v2"), rows.map { it.video.id.value })
        assertTrue(rows[0].blocked, "a blocked video must stay visible to the parent")
        assertFalse(rows[1].blocked)
    }

    @Test
    fun `an unknown channel lists nothing rather than failing`() {
        assertTrue(presenter.rows(ChannelId("UC_nope")).isEmpty())
    }

    @Test
    fun `blocking from the catalog takes effect immediately`() {
        seed()
        presenter.block(VideoId("v2"))
        assertTrue(presenter.rows(channelId).first { it.video.id.value == "v2" }.blocked)
    }

    @Test
    fun `unblocking from the catalog reverses it`() {
        seed()
        presenter.block(VideoId("v2"))
        presenter.unblock(VideoId("v2"))
        assertFalse(presenter.rows(channelId).first { it.video.id.value == "v2" }.blocked)
    }

    @Test
    fun `the catalog keeps its newest-first order`() {
        seed()
        assertEquals(listOf("First", "Second"), presenter.rows(channelId).map { it.video.title })
    }
}
