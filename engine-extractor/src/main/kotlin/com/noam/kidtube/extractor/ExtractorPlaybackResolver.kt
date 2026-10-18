package com.noam.kidtube.extractor

import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.core.PlaybackTicket
import com.noam.kidtube.core.VideoDetails
import com.noam.kidtube.engine.PlaybackResolver
import com.noam.kidtube.engine.VideoUnavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.services.youtube.PoTokenProvider
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType

class ExtractorPlaybackResolver(
    private val poTokenProvider: PoTokenProvider,
    private val downloader: Downloader = OkDownloader(),
    private val maxHeight: Int = 1080,
    private val preferredLocaleTag: String = "he",
) : PlaybackResolver {

    override suspend fun resolve(ticket: PlaybackTicket): Result<VideoDetails> =
        withContext(Dispatchers.IO) {
            ExtractorInit.ensureInitialised(downloader, poTokenProvider)
            val videoId = ticket.videoId.value

            try {
                Result.success(extract(videoId, ticket))
            } catch (cause: Throwable) {
                Result.failure(ExtractionFailures.classify(videoId, cause))
            }
        }

    private fun extract(videoId: String, ticket: PlaybackTicket): VideoDetails {
        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://www.youtube.com/watch?v=" + videoId,
        )

        val videos = buildList {
            info.videoStreams.forEach { stream ->
                add(
                    VideoCandidate(
                        url = stream.content.orEmpty(),
                        height = stream.height,
                        isMuxed = true,
                        mimeType = stream.format?.mimeType ?: "video/mp4",
                    ),
                )
            }
            info.videoOnlyStreams.forEach { stream ->
                add(
                    VideoCandidate(
                        url = stream.content.orEmpty(),
                        height = stream.height,
                        isMuxed = false,
                        mimeType = stream.format?.mimeType ?: "video/mp4",
                    ),
                )
            }
        }.filter { it.url.isNotBlank() }

        val audios = info.audioStreams
            .filter { !it.content.isNullOrBlank() }
            .map { stream ->
                AudioCandidate(
                    url = stream.content,
                    localeTag = stream.audioLocale?.toLanguageTag(),
                    isOriginal = stream.audioTrackType == AudioTrackType.ORIGINAL,
                    averageBitrate = stream.averageBitrate,
                    mimeType = stream.format?.mimeType ?: "audio/mp4",
                )
            }

        val chosen = StreamChooser.choose(videos, audios, maxHeight, preferredLocaleTag)
            ?: throw VideoUnavailable(videoId, "no playable stream combination was offered")

        return VideoDetails(
            id = ticket.videoId,
            title = info.name.orEmpty(),
            durationSeconds = info.duration,
            ageLimit = info.ageLimit,
            isLive = info.streamType == StreamType.LIVE_STREAM ||
                info.streamType == StreamType.AUDIO_LIVE_STREAM,
            source = PlaybackSource.Streams(
                videoUrl = chosen.video.url,
                audioUrl = chosen.audio?.url,
                mimeType = chosen.video.mimeType,
            ),
        )
    }
}
