package com.noam.kidtube.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deduplication, the `catalog/<id>.json` filename and every paging URL all key off the
 * resolved channel id, so it is the single point of failure behind "the same channel
 * added twice is one entry". The extractor derives it from whatever URL NewPipe returns;
 * this is the rule that decides whether that is trustworthy.
 */
class CanonicalChannelIdTest {

    @Test
    fun `a real channel id is canonical`() {
        assertTrue(ChannelUrlParser.isCanonicalChannelId("UCLsooMJoIpl_7ux2jvdPB-Q"))
        assertTrue(ChannelUrlParser.isCanonicalChannelId("UCHnyfMqiRRG1u-2MsSQLbXA"))
    }

    @Test
    fun `a handle is not a channel id`() {
        assertFalse(ChannelUrlParser.isCanonicalChannelId("@SuperSimpleSongs"))
        assertFalse(ChannelUrlParser.isCanonicalChannelId("SuperSimpleSongs"))
    }

    @Test
    fun `the wrong length is refused`() {
        assertFalse(ChannelUrlParser.isCanonicalChannelId("UC_short"))
        assertFalse(ChannelUrlParser.isCanonicalChannelId("UCLsooMJoIpl_7ux2jvdPB-Q-extra"))
    }

    @Test
    fun `a missing UC prefix is refused`() {
        assertFalse(ChannelUrlParser.isCanonicalChannelId("XXLsooMJoIpl_7ux2jvdPB-Q"))
    }

    @Test
    fun `empty and blank are refused`() {
        assertFalse(ChannelUrlParser.isCanonicalChannelId(""))
        assertFalse(ChannelUrlParser.isCanonicalChannelId("   "))
    }

    @Test
    fun `a path separator is refused, which is what keeps it safe as a filename`() {
        assertFalse(ChannelUrlParser.isCanonicalChannelId("UC/../../etc/passwd"))
    }
}
