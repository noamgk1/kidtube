package com.noam.kidtube.app.kid

/**
 * A duration badge, or an empty string when the length is not known.
 *
 * Unknown is the common case rather than an edge one: the RSS fast path carries no
 * duration, so any video that arrived since the last deep sync has none. A badge reading
 * "0:00" would be a lie; no badge is the truth.
 */
fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    val hours = seconds / 3_600
    val minutes = (seconds % 3_600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        hours.toString() + ":" + pad(minutes) + ":" + pad(secs)
    } else {
        minutes.toString() + ":" + pad(secs)
    }
}

/** How much of the video has been watched, for the thin bar across a thumbnail. */
fun progressFraction(positionMs: Long, durationSeconds: Long?): Float {
    if (durationSeconds == null || durationSeconds <= 0 || positionMs <= 0) return 0f
    return (positionMs.toFloat() / (durationSeconds * 1_000f)).coerceIn(0f, 1f)
}

private fun pad(value: Long): String = if (value < 10) "0" + value else value.toString()
