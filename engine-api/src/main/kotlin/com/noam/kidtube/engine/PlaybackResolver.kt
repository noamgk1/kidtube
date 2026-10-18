package com.noam.kidtube.engine

import com.noam.kidtube.core.PlaybackTicket
import com.noam.kidtube.core.VideoDetails

/** A catalogued video that no longer plays: deleted, made private, or region-blocked. */
class VideoUnavailable(val videoId: String, val detail: String) :
    Exception("video " + videoId + " is unavailable: " + detail)

/**
 * Takes a ticket rather than a bare id, so extraction cannot be performed for a video
 * that Policy has not already approved locally.
 */
interface PlaybackResolver {
    suspend fun resolve(ticket: PlaybackTicket): Result<VideoDetails>
}
