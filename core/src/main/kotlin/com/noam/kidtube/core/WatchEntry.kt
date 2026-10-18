package com.noam.kidtube.core

import kotlinx.serialization.Serializable

/** One viewing. Ids are plain strings so a parent reading a backup can follow it. */
@Serializable
data class WatchEntry(
    val videoId: String,
    val channelId: String?,
    val title: String,
    val startedAtEpochSeconds: Long,
    val watchedSeconds: Int,
    val completed: Boolean,
)

/** Per-video rollup for the parent's history screen. */
data class WatchAggregate(
    val videoId: String,
    val title: String,
    val plays: Int,
    val totalSeconds: Int,
    val lastAtEpochSeconds: Long,
)
