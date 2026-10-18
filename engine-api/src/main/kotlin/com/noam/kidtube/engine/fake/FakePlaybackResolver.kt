package com.noam.kidtube.engine.fake

import com.noam.kidtube.core.PlaybackSource
import com.noam.kidtube.core.PlaybackTicket
import com.noam.kidtube.core.VideoDetails
import com.noam.kidtube.core.VideoId
import com.noam.kidtube.engine.PlaybackResolver
import com.noam.kidtube.engine.VideoUnavailable

class FakePlaybackResolver : PlaybackResolver {

    private val scripted = mutableMapOf<String, VideoDetails>()

    fun scriptDetails(
        videoId: VideoId,
        ageLimit: Int = 0,
        isLive: Boolean = false,
        durationSeconds: Long = 100L,
    ) {
        scripted[videoId.value] = VideoDetails(
            id = videoId,
            title = "fake " + videoId.value,
            durationSeconds = durationSeconds,
            ageLimit = ageLimit,
            isLive = isLive,
            source = PlaybackSource.Streams("https://fake/" + videoId.value, null, "video/mp4"),
        )
    }

    override suspend fun resolve(ticket: PlaybackTicket): Result<VideoDetails> =
        scripted[ticket.videoId.value]
            ?.let { Result.success(it) }
            ?: Result.failure(VideoUnavailable(ticket.videoId.value, "not scripted"))
}
