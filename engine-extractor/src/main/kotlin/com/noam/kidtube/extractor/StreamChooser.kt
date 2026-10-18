package com.noam.kidtube.extractor

internal data class VideoCandidate(
    val url: String,
    val height: Int,
    /** A muxed stream carries its own audio, so no separate audio track is needed. */
    val isMuxed: Boolean,
    val mimeType: String,
)

internal data class AudioCandidate(
    val url: String,
    val localeTag: String?,
    val isOriginal: Boolean,
    val averageBitrate: Int,
    val mimeType: String,
)

internal data class ChosenStreams(val video: VideoCandidate, val audio: AudioCandidate?)

internal object StreamChooser {

    /**
     * Picks what to play. Two shapes matter:
     *
     *  - Adaptive: a video-only track plus a separate audio track, chosen by locale.
     *    Audio choice is not cosmetic — one measured channel offered 270 dubbed tracks,
     *    and taking the first would hand a child a language nobody in the house speaks.
     *  - Muxed only: the shape every Made-for-Kids video returned on 2026-10-17, a
     *    single 360p stream with audio already inside it.
     */
    fun choose(
        videos: List<VideoCandidate>,
        audios: List<AudioCandidate>,
        maxHeight: Int,
        preferredLocaleTag: String,
    ): ChosenStreams? {
        if (videos.isEmpty()) return null

        val audio = chooseAudio(audios, preferredLocaleTag)

        // Without an audio track, only a muxed stream is playable: a video-only track
        // would play silently, which is worse than reporting no choice at all.
        if (audio == null) {
            return pickByHeight(videos.filter { it.isMuxed }, maxHeight)
                ?.let { ChosenStreams(it, null) }
        }

        val adaptive = videos.filter { !it.isMuxed }
        val video = pickByHeight(adaptive, maxHeight) ?: pickByHeight(videos, maxHeight) ?: return null
        return ChosenStreams(video, if (video.isMuxed) null else audio)
    }

    private fun pickByHeight(candidates: List<VideoCandidate>, maxHeight: Int): VideoCandidate? {
        if (candidates.isEmpty()) return null
        return candidates.filter { it.height <= maxHeight }.maxByOrNull { it.height }
            // Everything is above the cap: take the least oversized rather than refuse.
            ?: candidates.minByOrNull { it.height }
    }

    private fun chooseAudio(
        candidates: List<AudioCandidate>,
        preferredLocaleTag: String,
    ): AudioCandidate? {
        if (candidates.isEmpty()) return null
        val preferredLanguage = preferredLocaleTag.substringBefore('-').lowercase()

        return candidates.firstOrNull {
            it.localeTag?.substringBefore('-')?.lowercase() == preferredLanguage
        }
            ?: candidates.firstOrNull { it.isOriginal }
            ?: candidates.maxByOrNull { it.averageBitrate }
    }
}
