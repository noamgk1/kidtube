package com.noam.kidtube.extractor

import com.noam.kidtube.engine.VideoUnavailable
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.PrivateContentException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The split that decides whether the UI may retry. A deleted, private or region-blocked
 * video never comes back, so retrying it is a loop a child sits in front of; a parser
 * break or a network blip is worth one more attempt.
 */
class ExtractionFailuresTest {

    @Test
    fun `content that is gone becomes a typed VideoUnavailable`() {
        val classified = ExtractionFailures.classify(
            "vid1",
            ContentNotAvailableException("This video is not available"),
        )
        val unavailable = assertIs<VideoUnavailable>(classified)
        assertEquals("vid1", unavailable.videoId)
        assertTrue(unavailable.detail.isNotBlank())
    }

    @Test
    fun `a private video is unavailable, not transient`() {
        assertIs<VideoUnavailable>(
            ExtractionFailures.classify("vid1", PrivateContentException("Private video")),
        )
    }

    @Test
    fun `a region-blocked video is unavailable`() {
        assertIs<VideoUnavailable>(
            ExtractionFailures.classify("vid1", GeographicRestrictionException("Not in your country")),
        )
    }

    @Test
    fun `an age-restricted video is unavailable at this layer too`() {
        assertIs<VideoUnavailable>(
            ExtractionFailures.classify("vid1", AgeRestrictedContentException("18+")),
        )
    }

    @Test
    fun `a network error stays transient so it may be retried`() {
        assertIs<IOException>(ExtractionFailures.classify("vid1", IOException("connection reset")))
    }

    @Test
    fun `a captcha challenge stays transient`() {
        assertIs<ReCaptchaException>(
            ExtractionFailures.classify("vid1", ReCaptchaException("captcha", "https://y")),
        )
    }

    @Test
    fun `a bare parsing error is not the video's fault and stays transient`() {
        // ContentNotAvailableException extends ParsingException, so the narrower check
        // must not swallow the wider one: a parser break means YouTube changed, not that
        // the video is gone.
        assertIs<ParsingException>(ExtractionFailures.classify("vid1", ParsingException("regex missed")))
    }

    @Test
    fun `an unknown extraction error stays an extraction error`() {
        assertIs<ExtractionException>(
            ExtractionFailures.classify("vid1", ExtractionException("something new")),
        )
    }

    @Test
    fun `a classified unavailable carries the original message for the parent log`() {
        val classified = ExtractionFailures.classify("vid1", ContentNotAvailableException("Private video"))
        assertTrue(assertIs<VideoUnavailable>(classified).detail.contains("Private video"))
    }

    @Test
    fun `classification is idempotent so re-wrapping does not nest`() {
        val once = ExtractionFailures.classify("vid1", ContentNotAvailableException("gone"))
        assertSame(once, ExtractionFailures.classify("vid1", once))
    }
}
