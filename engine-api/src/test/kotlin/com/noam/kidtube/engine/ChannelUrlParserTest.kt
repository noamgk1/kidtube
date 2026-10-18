package com.noam.kidtube.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ChannelUrlParserTest {

    private fun parse(raw: String) = ChannelUrlParser.parse(raw)

    @Test
    fun `a canonical channel url yields the id`() {
        val link = assertIs<PastedLink.ChannelById>(
            parse("https://www.youtube.com/channel/UCLsooMJoIpl_7ux2jvdPB-Q"),
        )
        assertEquals("UCLsooMJoIpl_7ux2jvdPB-Q", link.id.value)
        assertEquals("https://www.youtube.com/channel/UCLsooMJoIpl_7ux2jvdPB-Q", link.canonicalUrl)
    }

    @Test
    fun `a handle url yields the handle`() {
        val link = assertIs<PastedLink.ChannelByHandle>(parse("https://www.youtube.com/@SuperSimpleSongs"))
        assertEquals("SuperSimpleSongs", link.handle)
        assertEquals("https://www.youtube.com/@SuperSimpleSongs", link.canonicalUrl)
    }

    @Test
    fun `tracking parameters are stripped from the canonical url`() {
        val link = assertIs<PastedLink.ChannelByHandle>(
            parse("https://youtube.com/@SuperSimpleSongs?si=AbCdEf_123&feature=shared"),
        )
        assertEquals("https://www.youtube.com/@SuperSimpleSongs", link.canonicalUrl)
    }

    @Test
    fun `a tab suffix on a handle url is dropped`() {
        val link = assertIs<PastedLink.ChannelByHandle>(
            parse("https://www.youtube.com/@SuperSimpleSongs/videos"),
        )
        assertEquals("SuperSimpleSongs", link.handle)
    }

    @Test
    fun `a missing scheme is tolerated`() {
        assertIs<PastedLink.ChannelByHandle>(parse("youtube.com/@SuperSimpleSongs"))
    }

    @Test
    fun `the mobile host is accepted`() {
        assertIs<PastedLink.ChannelByHandle>(parse("https://m.youtube.com/@SuperSimpleSongs"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertIs<PastedLink.ChannelByHandle>(parse("  https://www.youtube.com/@Kids  "))
    }

    @Test
    fun `legacy c and user paths are recognised but marked legacy`() {
        val c = assertIs<PastedLink.ChannelByLegacyPath>(parse("https://www.youtube.com/c/kidstv123"))
        assertEquals("c", c.kind)
        assertEquals("kidstv123", c.name)

        val user = assertIs<PastedLink.ChannelByLegacyPath>(parse("https://www.youtube.com/user/kidstv123"))
        assertEquals("user", user.kind)
    }

    @Test
    fun `a watch url is a single video`() {
        val link = assertIs<PastedLink.SingleVideo>(parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", link.id.value)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", link.canonicalUrl)
    }

    @Test
    fun `a youtu dot be short link is a single video`() {
        val link = assertIs<PastedLink.SingleVideo>(parse("https://youtu.be/dQw4w9WgXcQ?t=30"))
        assertEquals("dQw4w9WgXcQ", link.id.value)
    }

    @Test
    fun `a watch url carrying a list parameter is still a single video`() {
        val link = assertIs<PastedLink.SingleVideo>(
            parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabc123"),
        )
        assertEquals("dQw4w9WgXcQ", link.id.value)
    }

    @Test
    fun `a shorts url is refused by name because shorts are out of scope`() {
        val link = assertIs<PastedLink.Unsupported>(parse("https://www.youtube.com/shorts/abcdefghijk"))
        assertEquals(UnsupportedReason.SHORTS, link.reason)
    }

    @Test
    fun `a live url is refused because live content cannot be reviewed in advance`() {
        val link = assertIs<PastedLink.Unsupported>(parse("https://www.youtube.com/live/abcdefghijk"))
        assertEquals(UnsupportedReason.LIVE, link.reason)
    }

    @Test
    fun `a playlist url is refused by name rather than silently misread`() {
        val link = assertIs<PastedLink.Unsupported>(parse("https://www.youtube.com/playlist?list=PLabc123"))
        assertEquals(UnsupportedReason.PLAYLIST, link.reason)
    }

    @Test
    fun `plain text is refused`() {
        assertEquals(
            UnsupportedReason.NOT_A_URL,
            assertIs<PastedLink.Unsupported>(parse("super simple songs")).reason,
        )
    }

    @Test
    fun `an empty paste is refused distinctly from malformed text`() {
        assertEquals(UnsupportedReason.EMPTY, assertIs<PastedLink.Unsupported>(parse("   ")).reason)
    }

    @Test
    fun `a non-youtube host is refused`() {
        assertEquals(
            UnsupportedReason.NOT_YOUTUBE,
            assertIs<PastedLink.Unsupported>(parse("https://vimeo.com/@kids")).reason,
        )
    }

    @Test
    fun `a lookalike host is refused rather than trusted`() {
        assertEquals(
            UnsupportedReason.NOT_YOUTUBE,
            assertIs<PastedLink.Unsupported>(parse("https://youtube.com.evil.test/@kids")).reason,
        )
    }

    @Test
    fun `a malformed channel id is refused instead of being sent to the network`() {
        assertEquals(
            UnsupportedReason.UNRECOGNISED,
            assertIs<PastedLink.Unsupported>(parse("https://www.youtube.com/channel/NOTACHANNELID")).reason,
        )
    }

    @Test
    fun `a malformed video id is refused`() {
        assertEquals(
            UnsupportedReason.UNRECOGNISED,
            assertIs<PastedLink.Unsupported>(parse("https://www.youtube.com/watch?v=tooshort")).reason,
        )
    }

    @Test
    fun `an unknown youtube path is refused`() {
        assertEquals(
            UnsupportedReason.UNRECOGNISED,
            assertIs<PastedLink.Unsupported>(parse("https://www.youtube.com/feed/subscriptions")).reason,
        )
    }

    @Test
    fun `a bare youtube host with no path is refused`() {
        assertEquals(
            UnsupportedReason.UNRECOGNISED,
            assertIs<PastedLink.Unsupported>(parse("https://www.youtube.com/")).reason,
        )
    }
}
