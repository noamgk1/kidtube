package com.noam.kidtube.core

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val passwordSaltBase64: String? = null,
    val passwordHashBase64: String? = null,
    val dailyLimitMinutes: Int = 60,
    /**
     * Whether titles the creator never published in Hebrew are machine-translated on the
     * device. Costs a one-off model download; the parent can turn it off.
     */
    val translateTitles: Boolean = true,
    /** Which PlayerEngine/ContentSource pair is active. "extractor" today. */
    val engineId: String = "extractor",
) {
    /** Keeps the hash out of logs and crash reports. */
    override fun toString(): String =
        "Settings(password=" + (if (passwordHashBase64 == null) "unset" else "set") +
            ", dailyLimitMinutes=$dailyLimitMinutes, translateTitles=$translateTitles" +
            ", engineId=$engineId)"
}
