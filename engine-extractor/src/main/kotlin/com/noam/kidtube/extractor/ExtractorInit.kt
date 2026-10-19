package com.noam.kidtube.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

/**
 * NewPipeExtractor keeps its downloader, localisation and PO token provider in
 * process-wide statics, so initialisation must happen exactly once and before any
 * extraction.
 */
internal object ExtractorInit {

    @Volatile
    private var done = false

    /**
     * [localeTag] is what YouTube is asked to answer in.
     *
     * Without it NewPipe asks in English and every title comes back in whatever the
     * creator typed. With it, YouTube returns its own translation of a title wherever
     * the creator published one - which is a real feature of YouTube's, not a guess made
     * here. Where no translation exists the original title is still what comes back:
     * nothing in this app invents a translation.
     */
    @Synchronized
    fun ensureInitialised(
        downloader: Downloader,
        poTokenProvider: PoTokenProvider,
        localeTag: String = "he",
    ) {
        if (done) return
        val language = localeTag.substringBefore('-')
        val country = localeTag.substringAfter('-', "IL")
        NewPipe.init(
            downloader,
            Localization(language, country),
            ContentCountry(country),
        )
        YoutubeStreamExtractor.setPoTokenProvider(poTokenProvider)
        done = true
    }
}
