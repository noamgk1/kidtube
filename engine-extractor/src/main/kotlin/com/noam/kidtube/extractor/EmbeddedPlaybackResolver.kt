package com.noam.kidtube.extractor

import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.core.PlaybackTicket
import com.noam.kidtube.core.VideoDetails
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.engine.PlaybackResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Resolves a video for YouTube's embedded player: all of the metadata, none of the
 * streams.
 *
 * The metadata still comes from a real extraction rather than from the catalog, and
 * deliberately. Policy's checks are made of it — that the video YouTube served is the one
 * that was asked for, its age limit, whether it is live — and a catalog entry carries
 * none of that. Skipping the extraction would save a request and quietly turn three
 * guarantees into assumptions.
 *
 * What it does skip is choosing a stream, which is the step that cannot do better than
 * 360p on Made-for-Kids content. A video whose streams are unusable but whose metadata
 * reads fine will play here and fails on the extractor engine.
 */
internal class EmbeddedPlaybackResolver(
    private val poTokenProvider: PoTokenProvider,
    private val downloader: Downloader = OkDownloader(),
) : PlaybackResolver {

    override suspend fun resolve(ticket: PlaybackTicket): Result<VideoDetails> =
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
