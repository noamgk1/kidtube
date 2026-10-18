package com.noam.kidtube.core

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class ChannelId(val value: String)

@JvmInline
@Serializable
value class VideoId(val value: String)
