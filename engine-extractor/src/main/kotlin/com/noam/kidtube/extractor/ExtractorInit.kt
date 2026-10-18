package com.noam.kidtube.extractor

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor

/**
 * NewPipeExtractor keeps its downloader and PO token provider in process-wide statics,
 * so initialisation must happen exactly once and before any extraction.
 */
internal object ExtractorInit {

    @Volatile
    private var done = false

    @Synchronized
    fun ensureInitialised(downloader: Downloader, poTokenProvider: PoTokenProvider) {
        if (done) return
        NewPipe.init(downloader)
        YoutubeStreamExtractor.setPoTokenProvider(poTokenProvider)
        done = true
    }
}
