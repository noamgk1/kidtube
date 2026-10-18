package com.noam.kidtube.extractor

import com.noam.kidtube.core.PlaybackSource

internal sealed interface MediaPlan {
    data class Single(val url: String, val mimeType: String) : MediaPlan
    data class Merged(val videoUrl: String, val audioUrl: String, val mimeType: String) : MediaPlan
}

/**
 * Decides what to hand ExoPlayer. Kept separate from the player so the decision is
 * testable on the JVM, while the thin wiring around it is the only part that needs a
 * device.
 */
internal object MediaPlanner {
    fun plan(source: PlaybackSource): MediaPlan? = when (source) {
        // An embed engine renders these, not this one.
        is PlaybackSource.Embedded -> null
        is PlaybackSource.Streams -> {
            // Bound to a local: a property from another module cannot be smart cast.
            val audioUrl = source.audioUrl
            when {
                source.videoUrl.isBlank() -> null
                audioUrl.isNullOrBlank() -> MediaPlan.Single(source.videoUrl, source.mimeType)
                else -> MediaPlan.Merged(source.videoUrl, audioUrl, source.mimeType)
            }
        }
    }
}
