package com.noam.kidtube.engine

/** Surfaced on the parent's status screen; never shown to a child. */
sealed interface EngineHealth {
    data object Healthy : EngineHealth

    /** Working, but below full capability - e.g. no PO token, so kids content caps at 360p. */
    data class Degraded(val reason: String) : EngineHealth

    data class Unavailable(val reason: String, val sinceEpochSeconds: Long) : EngineHealth
}
