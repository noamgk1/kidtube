package com.noam.kidtube.extractor

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The marshalling half of the PO token flow, which is pure and therefore testable
 * without a device. Everything here mirrors YouTube's wire format as implemented by
 * NewPipe; the expectations are written independently of the production code so that a
 * matching bug in both would still show up.
 */
class PoTokenCodecTest {

    // ---- u8ToBase64 -------------------------------------------------------------

    @Test
    fun `comma separated bytes become base64`() {
        // 97,98,99 is "abc"
        assertEquals("YWJj", PoTokenCodec.u8ToBase64("97,98,99"))
    }

    @Test
    fun `bytes above 127 are treated as unsigned`() {
        // A signed-byte bug here would produce the wrong token and a silent 403.
        assertEquals("+/8=", Base64.getEncoder().encodeToString(byteArrayOf(-5, -1)))
        assertEquals("-_8=", PoTokenCodec.u8ToBase64("251,255"))
    }

    @Test
    fun `the result uses the url-safe alphabet`() {
        val encoded = PoTokenCodec.u8ToBase64("251,255")
        assertTrue(!encoded.contains('+') && !encoded.contains('/'), "got $encoded")
    }

    @Test
    fun `a single byte encodes with padding`() {
        assertEquals("YQ==", PoTokenCodec.u8ToBase64("97"))
    }

    @Test
    fun `non numeric input is rejected rather than producing a silent wrong token`() {
        assertFailsWith<NumberFormatException> { PoTokenCodec.u8ToBase64("97,not-a-byte") }
    }

    // ---- stringToU8 ------------------------------------------------------------

    @Test
    fun `an ascii identifier becomes a javascript Uint8Array literal`() {
        assertEquals("new Uint8Array([97,98,99])", PoTokenCodec.stringToU8("abc"))
    }

    @Test
    fun `a multibyte identifier is encoded as utf-8 bytes`() {
        // Hebrew aleph is U+05D0 -> 0xD7 0x90 -> 215,144
        assertEquals("new Uint8Array([215,144])", PoTokenCodec.stringToU8("א"))
    }

    @Test
    fun `an eleven character video id produces eleven bytes`() {
        val literal = PoTokenCodec.stringToU8("dQw4w9WgXcQ")
        assertEquals(11, literal.removePrefix("new Uint8Array([").removeSuffix("])").split(",").size)
    }

    // ---- parseIntegrityTokenData ----------------------------------------------

    @Test
    fun `the integrity token becomes a Uint8Array literal and a ttl`() {
        val tokenBytes = byteArrayOf(1, 2, 3)
        val raw = "[\"" + urlSafe(tokenBytes) + "\",3600]"
        val (literal, ttl) = PoTokenCodec.parseIntegrityTokenData(raw)
        assertEquals("new Uint8Array([1,2,3])", literal)
        assertEquals(3_600L, ttl)
    }

    @Test
    fun `the youtube base64 dialect maps dash underscore and dot`() {
        // YouTube uses - for +, _ for / and . for = padding.
        val raw = "[\"-_8.\",60]"
        val (literal, ttl) = PoTokenCodec.parseIntegrityTokenData(raw)
        assertEquals("new Uint8Array([251,255])", literal)
        assertEquals(60L, ttl)
    }

    @Test
    fun `malformed integrity data is rejected`() {
        assertFailsWith<Exception> { PoTokenCodec.parseIntegrityTokenData("not json") }
    }

    // ---- parseChallengeData ---------------------------------------------------

    private val challengeInner =
        "[\"msg-1\",[\"safe-script\"],[\"trusted-url\"],\"hash-1\",\"program-1\"," +
            "\"globalName-1\",null,\"blob-1\"]"

    @Test
    fun `an unscrambled challenge is reshaped for the javascript side`() {
        val parsed = PoTokenCodec.parseChallengeData("[$challengeInner]")

        assertTrue(parsed.contains("\"messageId\":\"msg-1\""), parsed)
        assertTrue(parsed.contains("\"interpreterHash\":\"hash-1\""), parsed)
        assertTrue(parsed.contains("\"program\":\"program-1\""), parsed)
        assertTrue(parsed.contains("\"globalName\":\"globalName-1\""), parsed)
        assertTrue(parsed.contains("\"clientExperimentsStateBlob\":\"blob-1\""), parsed)
        assertTrue(
            parsed.contains("\"privateDoNotAccessOrElseSafeScriptWrappedValue\":\"safe-script\""),
            parsed,
        )
        assertTrue(
            parsed.contains(
                "\"privateDoNotAccessOrElseTrustedResourceUrlWrappedValue\":\"trusted-url\"",
            ),
            parsed,
        )
    }

    @Test
    fun `a scrambled challenge is descrambled first`() {
        // The inverse of descramble, written here independently: subtract 97 per byte,
        // then encode with YouTube's base64 dialect.
        val scrambled = urlSafe(
            challengeInner.toByteArray(Charsets.UTF_8).map { (it - 97).toByte() }.toByteArray(),
        )
        val parsed = PoTokenCodec.parseChallengeData("[0,\"$scrambled\"]")
        assertTrue(parsed.contains("\"messageId\":\"msg-1\""), parsed)
        assertTrue(parsed.contains("\"program\":\"program-1\""), parsed)
    }

    @Test
    fun `a challenge whose wrapped values are absent still parses`() {
        val inner = "[\"msg-2\",null,null,\"hash-2\",\"program-2\",\"global-2\",null,\"blob-2\"]"
        val parsed = PoTokenCodec.parseChallengeData("[$inner]")
        assertTrue(parsed.contains("\"messageId\":\"msg-2\""), parsed)
    }

    @Test
    fun `malformed challenge data is rejected`() {
        assertFailsWith<Exception> { PoTokenCodec.parseChallengeData("{}") }
    }

    private fun urlSafe(bytes: ByteArray): String =
        Base64.getEncoder().encodeToString(bytes)
            .replace('+', '-')
            .replace('/', '_')
            .replace('=', '.')
}
