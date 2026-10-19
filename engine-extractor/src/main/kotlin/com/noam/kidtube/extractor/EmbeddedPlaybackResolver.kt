package com.noam.kidtube.extractor

import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.core.PlaybackTicket
import com.noam.kidtube.core.VideoDetails
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.engine.PlaybackResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Resolves a video for YouTube's embedded player.
 *
 * The embedded player needs nothing but a video id, so when the video is already in the
 * approved catalog this answers from there and makes no network request at all. That is
 * the whole of the wait before a video starts: extraction was measured between 1.8 and
 * 26.6 seconds, and on this path it buys nothing that is used.
 *
 * What the skipped extraction did buy was three of Policy's checks, so it is worth being
 * exact about what replaces them:
 *
 *  - **Only approved videos play.** Unchanged, and it never came from here: Policy.issue
 *    checks the whitelist before a ticket exists, and the player accepts nothing but a
 *    ConfirmedTicket.
 *  - **Live streams** are already excluded when the catalog is built, so one cannot be in
 *    the catalog to be found here.
 *  - **Age-restricted video** is the one real difference. YouTube refuses to play such a
 *    video in an embed and the player reports its refusal, so it is enforced - by YouTube
 *    rather than by us, one step later, and visibly.
 *
 * A video that is *not* in the catalog still resolves the slow, fully-checked way.
 */
internal class EmbeddedPlaybackResolver(
    private val knownVideo: (VideoId) -> VideoRef?,
    private val poTokenProvider: PoTokenProvider,
    private val downloader: Downloader = OkDownloader(),
) : PlaybackResolver {

    override suspend fun resolve(ticket: PlaybackTicket): Result<VideoDetails> {
        knownVideo(ticket.videoId)?.let { known ->
            return Result.success(
                VideoDetails(
                    id = ticket.videoId,
                    title = known.title,
                    // The RSS fast path carries no duration; the player reports the real
                    // one as soon as it has loaded.
                    durationSeconds = known.durationSeconds ?: 0,
                    ageLimit = 0,
                    isLive = false,
                    source = PlaybackSource.Embedded(ticket.videoId),
                ),
            )
        }
        return extract(ticket)
    }

    /** For a video with no catalog entry - an individually approved one, say. */
    private suspend fun extract(ticket: PlaybackTicket): Result<VideoDetails> =
        withContext(Dispatchers.IO) {
            ExtractorInit.ensureInitialised(downloader, poTokenProvider)
            val videoId = ticket.videoId.value

            try {
                val info = StreamInfo.getInfo(
                    ServiceList.YouTube,
                    "https://www.youtube.com/watch?v=" + videoId,
                )

                Result.success(
                    VideoDetails(
                        // The extracted id, not the requested one: copying the ticket's
                        // id back would make Policy.confirm's identity check
                        // tautological, and YouTube does serve a substitute for merged
                        // or replaced uploads.
                        id = VideoId(info.id.orEmpty().ifBlank { videoId }),
                        title = info.name.orEmpty(),
                        durationSeconds = info.duration,
                        ageLimit = info.ageLimit,
                        isLive = info.streamType == StreamType.LIVE_STREAM ||
                            info.streamType == StreamType.AUDIO_LIVE_STREAM,
                        source = PlaybackSource.Embedded(VideoId(videoId)),
                    ),
                )
            } catch (cause: Throwable) {
                Result.failure(ExtractionFailures.classify(videoId, cause))
            }
        }
}
