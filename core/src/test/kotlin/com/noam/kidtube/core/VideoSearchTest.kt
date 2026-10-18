package com.noam.kidtube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Search runs over approved content only. It is a way to find something already on the
 * whitelist, never a way to reach YouTube, so nothing here touches the network.
 */
class VideoSearchTest {

    private val channelId = ChannelId("UC_abc")

    private fun video(id: String, title: String, published: Long = 100) =
        VideoRef(VideoId(id), channelId, title, null, 120L, published)

    private val library = listOf(
        video("v1", "פעילות לילדים בגינה"),
        video("v2", "שיר הא-ב"),
        video("v3", "Baby Shark Dance"),
        video("v4", "לומדים לספור עד עשר"),
        video("v5", "מה עושות הציפורים בחורף"),
    )

    private fun titles(query: String, items: List<VideoRef> = library) =
        VideoSearch.search(items, query).map { it.title }

    @Test
    fun `an empty query matches nothing rather than everything`() {
        assertTrue(VideoSearch.search(library, "").isEmpty())
        assertTrue(VideoSearch.search(library, "   ").isEmpty())
    }

    @Test
    fun `a hebrew word is found inside a title`() {
        assertEquals(listOf("פעילות לילדים בגינה"), titles("ילדים"))
    }

    @Test
    fun `search is case insensitive for latin titles`() {
        assertEquals(listOf("Baby Shark Dance"), titles("baby"))
        assertEquals(listOf("Baby Shark Dance"), titles("SHARK"))
    }

    @Test
    fun `a prefix of a word matches`() {
        assertEquals(listOf("לומדים לספור עד עשר"), titles("לספ"))
    }

    @Test
    fun `several words all have to appear, in any order`() {
        assertEquals(listOf("לומדים לספור עד עשר"), titles("עשר לומדים"))
        assertTrue(titles("לומדים ציפורים").isEmpty(), "no title holds both")
    }

    @Test
    fun `niqqud in the library is ignored so plain typing finds it`() {
        val pointed = listOf(video("p1", "שִׁיר הָעֶרֶשׂ"))
        assertEquals(listOf("שִׁיר הָעֶרֶשׂ"), VideoSearch.search(pointed, "שיר").map { it.title })
    }

    @Test
    fun `niqqud typed by the child is ignored too`() {
        assertEquals(listOf("שיר הא-ב"), titles("שִׁיר"))
    }

    @Test
    fun `the hebrew geresh and an apostrophe are treated alike`() {
        val items = listOf(video("g1", "ג׳ירפה בגן"))
        assertEquals(1, VideoSearch.search(items, "ג'ירפה").size)
        assertEquals(1, VideoSearch.search(items, "ג׳ירפה").size)
    }

    @Test
    fun `punctuation and extra spaces do not prevent a match`() {
        assertEquals(listOf("שיר הא-ב"), titles("  הא ב  "))
    }

    @Test
    fun `a title that starts with the query ranks above one that merely contains it`() {
        val items = listOf(
            video("a", "שירים על חיות"),
            video("b", "אוסף שירים"),
        )
        assertEquals(listOf("שירים על חיות", "אוסף שירים"), VideoSearch.search(items, "שירים").map { it.title })
    }

    @Test
    fun `a word-start match ranks above a mid-word match`() {
        val items = listOf(
            video("mid", "מכונית אדומה"),
            video("start", "אדומה וגדולה"),
        )
        assertEquals("אדומה וגדולה", VideoSearch.search(items, "אדומה").first().title)
    }

    @Test
    fun `ties are broken by newest first`() {
        val items = listOf(
            video("old", "שיר ערש", published = 100),
            video("new", "שיר ערש", published = 900),
        )
        assertEquals(listOf("new", "old"), VideoSearch.search(items, "שיר ערש").map { it.id.value })
    }

    @Test
    fun `nothing matching yields an empty list rather than everything`() {
        assertTrue(titles("קרנף").isEmpty())
    }

    @Test
    fun `results are capped so a one-letter query cannot render thousands of tiles`() {
        val many = (1..500).map { video("v" + it, "שיר מספר " + it) }
        assertEquals(VideoSearch.MAX_RESULTS, VideoSearch.search(many, "שיר").size)
    }

    @Test
    fun `a duplicate id appears once`() {
        val dupes = listOf(video("same", "שיר ערש"), video("same", "שיר ערש"))
        assertEquals(1, VideoSearch.search(dupes, "שיר").size)
    }

    @Test
    fun `final letter forms match their ordinary forms`() {
        // A child typing "ציפורים" should find a title written with the final mem, and
        // typing the stem should still reach it.
        val items = listOf(video("f1", "ציפורים בחלון"))
        assertEquals(1, VideoSearch.search(items, "ציפור").size)
    }
}
