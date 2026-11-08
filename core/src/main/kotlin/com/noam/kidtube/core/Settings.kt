package com.noam.kidtube.core

import kotlinx.serialization.Serializable

@Serializable
data class Settings(
    val passwordSaltBase64: String? = null,
    val passwordHashBase64: String? = null,
    val dailyLimitMinutes: Int = 60,
    /**
     * Whether titles the creator never published in Hebrew are machine-translated on the
     * device.
     *
     * Kept, and kept defaulting to on, though no translator ships in this build - see
     * [TitleTranslator.None]. It gates the translation pass rather than describing a
     * capability, so a build that plugs a translator in needs no settings migration, and
     * an existing tablet's exported backup still restores into one.
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
