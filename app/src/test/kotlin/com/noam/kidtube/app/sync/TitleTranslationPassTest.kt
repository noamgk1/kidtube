package com.noam.kidtube.app.sync

import com.noam.kidtube.core.Channel
import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.Settings
import com.noam.kidtube.core.TitleTranslator
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.FileStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.TranslationStore
import com.noam.kidtube.core.store.WhitelistStore
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Scriptable stand-in: records what it was asked, answers what it was told to. */
private class FakeTranslator(
    private val answers: Map<String, String> = emptyMap(),
    private val ready: Boolean = true,
) : TitleTranslator {
    var asked: List<List<String>> = emptyList()
        private set
    var prepareCalls: Int = 0
        private set

    override suspend fun prepare(): Boolean {
        prepareCalls++
        return ready
    }

    override suspend fun translate(titles: List<String>): Map<String, String> {
        asked = asked + listOf(titles)
        return titles.mapNotNull { title -> answers[title]?.let { title to it } }.toMap()
    }
}

/**
 * Filling the translation cache for everything already approved.
 *
 * This runs over the stored catalogs rather than at sync time, so a parent who turns
 * translation on after approving a channel gets Hebrew for what is already there instead
 * of having to rebuild the catalog.
 */
class TitleTranslationPassTest {

    private val root: File = Files.createTempDirectory("translate-pass").toFile()
    private val files = FileStore(root)
    private val whitelistStore = WhitelistStore(files)
    private val catalogStore = CatalogStore(files)
    private val translationStore = TranslationStore(files)
    private val settingsStore = SettingsStore(files)

    private val kids = ChannelId("UC_kids")

    @AfterTest
    fun tearDown() { root.deleteRecursively() }

    private fun pass(translator: TitleTranslator) = TitleTranslationPass(
        whitelistStore = whitelistStore,
        catalogStore = catalogStore,
        translationStore = translationStore,
        settingsStore = settingsStore,
        translator = translator,
    )

    private fun approveChannelWith(vararg titles: String) {
        whitelistStore.addChannel(Channel(kids, "Kids", null, "feed", 1L))
        catalogStore.save(
            kids,
            titles.mapIndexed { index, title ->
                VideoRef(VideoId("v" + index), kids, title, null, 60L, index.toLong())
            },
        )
    }

    @Test
    fun `every approved title is translated and kept`() = runTest {
        approveChannelWith("Baby Shark", "Five Little Ducks")
        val translator = FakeTranslator(
            mapOf("Baby Shark" to "בייבי", "Five Little Ducks" to "ברווזים"),
        )

        assertEquals(2, pass(translator).run())

        assertEquals("בייבי", translationStore.all()["Baby Shark"])
        assertEquals("ברווזים", translationStore.all()["Five Little Ducks"])
    }

    @Test
    fun `a second run asks the translator nothing`() = runTest {
        approveChannelWith("Baby Shark")
        val translator = FakeTranslator(mapOf("Baby Shark" to "בייבי"))

        pass(translator).run()
        val again = pass(translator).run()

        assertEquals(0, again)
        assertEquals(
            1,
            translator.asked.size,
            "a cached title must not be sent to the translator again",
        )
    }

    @Test
    fun `individually approved videos are translated too`() = runTest {
        whitelistStore.addSingleVideo(
            VideoRef(VideoId("solo"), ChannelId(""), "A Single Song", null, null, null),
        )
        val translator = FakeTranslator(mapOf("A Single Song" to "שיר אחד"))

        assertEquals(1, pass(translator).run())
        assertEquals("שיר אחד", translationStore.all()["A Single Song"])
    }

    @Test
    fun `a title the translator could not handle is left alone rather than cached wrong`() =
        runTest {
            approveChannelWith("Baby Shark", "Untranslatable")
            val translator = FakeTranslator(mapOf("Baby Shark" to "בייבי"))

            pass(translator).run()

            assertTrue(translationStore.all().containsKey("Baby Shark"))
            assertTrue(
                !translationStore.all().containsKey("Untranslatable"),
                "caching a failure would make it permanent",
            )
        }

    @Test
    fun `nothing happens when the parent has turned translation off`() = runTest {
        approveChannelWith("Baby Shark")
        settingsStore.save(settingsStore.load().copy(translateTitles = false))
        val translator = FakeTranslator(mapOf("Baby Shark" to "בייבי"))

        assertEquals(0, pass(translator).run())
        assertEquals(0, translator.prepareCalls, "the 30MB model must not be fetched when off")
        assertTrue(translationStore.all().isEmpty())
    }

    @Test
    fun `an unavailable model leaves the titles as they are`() = runTest {
        approveChannelWith("Baby Shark")
        val translator = FakeTranslator(answers = mapOf("Baby Shark" to "x"), ready = false)

        assertEquals(0, pass(translator).run())
        assertTrue(translator.asked.isEmpty(), "nothing to translate with, so nothing is asked")
        assertTrue(translationStore.all().isEmpty())
    }

    @Test
    fun `a large catalog is translated in batches`() = runTest {
        // A deeply synced channel runs to hundreds of titles, and handing all of them to
        // the translator in one call is what makes it stall.
        approveChannelWith(*(1..250).map { "Song " + it }.toTypedArray())
        val translator = FakeTranslator((1..250).associate { "Song $it" to "שיר $it" })

        pass(translator).run()

        assertTrue(
            translator.asked.all { it.size <= TitleTranslationPass.BATCH },
            "batches of at most " + TitleTranslationPass.BATCH,
        )
        assertEquals(250, translationStore.all().size)
    }

    @Test
    fun `a blocked video is still translated, because the parent may unblock it`() = runTest {
        approveChannelWith("Baby Shark")
        whitelistStore.block(VideoId("v0"))
        val translator = FakeTranslator(mapOf("Baby Shark" to "בייבי"))

        assertEquals(1, pass(translator).run())
    }
}
