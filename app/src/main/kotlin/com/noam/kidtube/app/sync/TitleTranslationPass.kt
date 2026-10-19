package com.noam.kidtube.app.sync

import com.noam.kidtube.core.TitleTranslator
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.TranslationStore
import com.noam.kidtube.core.store.WhitelistStore

/**
 * Fills the translation cache for everything the child is allowed to watch.
 *
 * It walks the stored catalogs rather than translating as videos arrive, which is what
 * lets a parent turn translation on *after* approving a channel and get Hebrew for the
 * seven hundred videos already there. Running it again is cheap: the cache answers for
 * everything it has seen, so only genuinely new titles reach the translator.
 */
class TitleTranslationPass(
    private val whitelistStore: WhitelistStore,
    private val catalogStore: CatalogStore,
    private val translationStore: TranslationStore,
    private val settingsStore: SettingsStore,
    private val translator: TitleTranslator,
) {

    /** Returns how many titles were newly translated. */
    suspend fun run(): Int {
        if (!settingsStore.load().translateTitles) return 0

        val whitelist = whitelistStore.load()
        val titles = whitelist.channels.values
            .flatMap { catalogStore.load(it.id) }
            .plus(whitelist.singleVideos.values)
            // Blocked videos are translated too: blocking is reversible, and the cost of
            // a title is paid once either way.
            .map { it.title }

        val outstanding = translationStore.missing(titles)
        if (outstanding.isEmpty()) return 0

        // Only now is the model worth fetching - 30MB, and pointless if there is nothing
        // left to translate.
        if (!translator.prepare()) return 0

        var translated = 0
        outstanding.chunked(BATCH).forEach { batch ->
            val answers = translator.translate(batch)
            // Whatever came back is kept; what did not is left out of the cache rather
            // than stored as a failure, so a later pass can try it again.
            translationStore.putAll(answers)
            translated += answers.size
        }
        return translated
    }

    companion object {
        /**
         * Titles per call. A deeply synced channel runs to hundreds, and handing them
         * over in one go is what makes the translator stall.
         */
        const val BATCH: Int = 50
    }
}
