package com.noam.kidtube.core.store

import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.VideoId
import kotlinx.serialization.Serializable

private const val FILE = "resume.json"

@Serializable
data class ResumePoint(
    val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochSeconds: Long,
)

@Serializable
private data class ResumeState(val points: List<ResumePoint> = emptyList())

/**
 * Where each video was left off, and what belongs in a "continue watching" row.
 *
 * Two rules keep that row honest. A glance is not progress: under [MIN_PROGRESS_MS] the
 * child tapped and changed their mind, and offering to resume that is noise. And a video
 * watched to within [END_MARGIN_MS] of its end is finished rather than half-watched, so
 * it is forgotten instead of inviting a replay of the credits.
 */
class ResumeStore(
    private val files: FileStore,
    private val clock: Clock,
) {

    fun save(videoId: VideoId, positionMs: Long, durationMs: Long) {
        val state = load()
        val others = state.points.filterNot { it.videoId == videoId.value }

        if (!worthRemembering(positionMs, durationMs)) {
            // Includes reaching the end, which clears any earlier position.
            write(ResumeState(others))
            return
        }

        val point = ResumePoint(
            videoId = videoId.value,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochSeconds = clock.nowEpochMillis() / 1_000,
        )
        val kept = (listOf(point) + others)
            .sortedByDescending { it.updatedAtEpochSeconds }
            .take(MAX_REMEMBERED)
        write(ResumeState(kept))
    }

    /** Milliseconds to resume from, or 0 when there is nothing worth resuming. */
    fun positionFor(videoId: VideoId): Long =
        load().points.firstOrNull { it.videoId == videoId.value }?.positionMs ?: 0L

    /** Most recently watched first. */
    fun continueWatching(limit: Int = MAX_REMEMBERED): List<ResumePoint> =
        load().points
            .sortedByDescending { it.updatedAtEpochSeconds }
            .take(limit)

    fun clear(videoId: VideoId) {
        val state = load()
        write(ResumeState(state.points.filterNot { it.videoId == videoId.value }))
    }

    private fun worthRemembering(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs < MIN_PROGRESS_MS) return false
        // A duration of zero means the player never reported one; trust the position.
        if (durationMs <= 0) return true
        return positionMs < durationMs - END_MARGIN_MS
    }

    private fun load(): ResumeState =
        files.read(FILE, ResumeState.serializer()) { ResumeState() }

    private fun write(state: ResumeState) {
        files.write(FILE, ResumeState.serializer(), state)
    }

    companion object {
        /** Below this, the child was only looking. */
        const val MIN_PROGRESS_MS: Long = 10_000

        /** This close to the end, the video is finished. */
        const val END_MARGIN_MS: Long = 15_000

        const val MAX_REMEMBERED: Int = 30
    }
}
