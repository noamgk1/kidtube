package com.noam.kidtube.core

import kotlinx.serialization.Serializable

/** Progress of a deep catalog sync, so the parent can see a catalog is still partial. */
@Serializable
data class SyncState(
    val channelId: String,
    val pagesDone: Int = 0,
    val videosStored: Int = 0,
    val complete: Boolean = false,
    val lastError: String? = null,
    val updatedAtEpochSeconds: Long = 0,
)
