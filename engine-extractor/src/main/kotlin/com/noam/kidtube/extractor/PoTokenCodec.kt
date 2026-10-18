package com.noam.kidtube.extractor

import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import java.util.Base64

/**
 * Marshalling between YouTube's BotGuard wire format and the JavaScript running in the
 * WebView. Pure functions, so the fiddly parts — unsigned bytes, YouTube's base64
 * dialect, the scrambled challenge — are covered by ordinary unit tests rather than
 * discovered on a tablet.
 *
 * The shapes here follow YouTube's attestation protocol as implemented by NewPipe
 * (GPLv3, as is this app).
 */
internal object PoTokenCodec {

    /**
     * Reshapes the raw Create-endpoint response into the object the page's
     * runBotGuard() expects. The response arrives either plain, or with the real
     * payload scrambled into element 1.
     */
    fun parseChallengeData(rawChallengeData: String): String {
        val scrambled = JsonParser.array().from(rawChallengeData)

        val challengeData = if (scrambled.size > 1 && scrambled.isString(1)) {
            JsonParser.array().from(descramble(scrambled.getString(1)))
        } else {
            scrambled.getArray(0)
        }

        val messageId = challengeData.getString(0)
        val interpreterHash = challengeData.getString(3)
        val program = challengeData.getString(4)
        val globalName = challengeData.getString(5)
        val clientExperimentsStateBlob = challengeData.getString(7)

        val safeScript = challengeData.getArray(1, null)?.find { it is String }
        val trustedResourceUrl = challengeData.getArray(2, null)?.find { it is String }

        return JsonWriter.string(
            JsonObject.builder()
                .value("messageId", messageId)
                .`object`("interpreterJavascript")
                .value("privateDoNotAccessOrElseSafeScriptWrappedValue", safeScript)
                .value("privateDoNotAccessOrElseTrustedResourceUrlWrappedValue", trustedResourceUrl)
                .end()
                .value("interpreterHash", interpreterHash)
                .value("program", program)
                .value("globalName", globalName)
                .value("clientExperimentsStateBlob", clientExperimentsStateBlob)
                .done(),
        )
    }

    /**
     * Turns the GenerateIT response into a JavaScript Uint8Array literal plus the
     * token's lifetime in seconds.
     */
    fun parseIntegrityTokenData(rawIntegrityTokenData: String): Pair<String, Long> {
        val data = JsonParser.array().from(rawIntegrityTokenData)
        return newUint8Array(youtubeBase64ToBytes(data.getString(0))) to data.getLong(1)
    }

    /** Encodes an identifier (a visitorData blob or a video id) for the JS call. */
    fun stringToU8(identifier: String): String =
        newUint8Array(identifier.toByteArray(Charsets.UTF_8))

    /**
     * Takes Uint8Array::toString() output ("97,98,99") and produces the url-safe base64
     * that YouTube expects for a PO token. Bytes arrive unsigned, so they are widened
     * before narrowing — a signed read here yields a wrong token and a silent 403.
     */
    fun u8ToBase64(poToken: String): String =
        Base64.getEncoder()
            .encodeToString(
                poToken.split(",").map { it.trim().toUByte().toByte() }.toByteArray(),
            )
            .replace('+', '-')
            .replace('/', '_')

    /** Decodes the scrambled challenge: YouTube base64, then add 97 to every byte. */
    private fun descramble(scrambledChallenge: String): String =
        youtubeBase64ToBytes(scrambledChallenge)
            .map { (it + 97).toByte() }
            .toByteArray()
            .toString(Charsets.UTF_8)

    private fun newUint8Array(contents: ByteArray): String =
        "new Uint8Array([" +
            contents.joinToString(separator = ",") { it.toUByte().toString() } +
            "])"

    /** YouTube's base64 dialect: - for +, _ for / and . for = padding. */
    private fun youtubeBase64ToBytes(base64: String): ByteArray =
        Base64.getDecoder().decode(
            base64.replace('-', '+').replace('_', '/').replace('.', '='),
        )
}
