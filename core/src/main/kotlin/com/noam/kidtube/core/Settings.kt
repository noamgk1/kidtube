package com.noam.kidtube.core

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val passwordSaltBase64: String? = null,
    val passwordHashBase64: String? = null,
    val dailyLimitMinutes: Int = 60,
    /** Which PlayerEngine/ContentSource pair is active. "extractor" today. */
    val engineId: String = "extractor",
) {
    /** Keeps the hash out of logs and crash reports. */
    override fun toString(): String =
        "Settings(password=" + (if (passwordHashBase64 == null) "unset" else "set") +
            ", dailyLimitMinutes=$dailyLimitMinutes, engineId=$engineId)"
}
