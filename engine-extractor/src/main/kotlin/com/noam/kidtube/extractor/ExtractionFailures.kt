package com.noam.kidtube.extractor

import com.noam.kidtube.engine.VideoUnavailable
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException

/**
 * Separates "this video is gone" from "something broke". The distinction decides whether
 * the UI may retry: a deleted, private, region-blocked, paid or age-restricted video
 * never comes back, so retrying it is a loop a child would sit in front of.
 *
 * The library's hierarchy makes this one check: every permanently-gone case extends
 * [ContentNotAvailableException], while its own parent [ParsingException] means the
 * parser broke — YouTube changed, not the video — and stays retryable.
 */
object ExtractionFailures {

    fun classify(videoId: String, cause: Throwable): Throwable = when (cause) {
        is VideoUnavailable -> cause
        is ContentNotAvailableException ->
            VideoUnavailable(videoId, cause.message ?: "content not available")
        else -> cause
    }
}
