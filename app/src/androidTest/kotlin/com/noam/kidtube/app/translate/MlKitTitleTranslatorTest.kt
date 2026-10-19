package com.noam.kidtube.app.translate

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.test.assertTrue

/**
 * Proves the on-device translator actually works on a device: that the Hebrew model can
 * be fetched, and that what comes back is Hebrew.
 *
 * Needs the network for the first run - the model is about 30MB - and nothing after
 * that. The assertion is deliberately about the alphabet rather than about particular
 * words: this is machine translation, and pinning it to an exact phrase would be a test
 * of Google's model rather than of this app's wiring.
 */
class MlKitTitleTranslatorTest {

    private val hebrew = '֐'..'׿'

    @Test
    fun theModelDownloadsAndTranslatesIntoHebrew() = runBlocking {
        val translator = MlKitTitleTranslator()
        try {
            val ready = withTimeout(MODEL_TIMEOUT_MS) { translator.prepare() }
            assertTrue(ready, "the Hebrew model could not be fetched")

            val answers = withTimeout(TRANSLATE_TIMEOUT_MS) {
                translator.translate(listOf("Five Little Ducks", "The Wheels on the Bus"))
            }

            assertTrue(answers.size == 2, "both titles should have come back: " + answers)
            answers.forEach { (original, translated) ->
                assertTrue(
                    translated.any { it in hebrew },
                    "'" + original + "' came back as '" + translated + "', which has no Hebrew in it",
                )
            }
        } finally {
            translator.close()
        }
    }

    @Test
    fun preparingTwiceDoesNotFetchTheModelAgain() = runBlocking {
        val translator = MlKitTitleTranslator()
        try {
            withTimeout(MODEL_TIMEOUT_MS) { translator.prepare() }
            // Already on the device, so this must not be a download: a second call that
            // went to the network would make every pass over the catalog expensive.
            val startedAt = System.currentTimeMillis()
            val ready = withTimeout(5_000) { translator.prepare() }
            val elapsed = System.currentTimeMillis() - startedAt

            assertTrue(ready)
            assertTrue(elapsed < 1_000, "the second prepare took " + elapsed + "ms")
        } finally {
            translator.close()
        }
    }

    private companion object {
        /** A 30MB model over an emulator's network. */
        const val MODEL_TIMEOUT_MS = 300_000L
        const val TRANSLATE_TIMEOUT_MS = 60_000L
    }
}
