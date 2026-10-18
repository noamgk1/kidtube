package com.noam.kidtube.app.kid

sealed interface TimeLimitState {
    /** Say nothing. A clock running through every video only breeds negotiation. */
    data object Plenty : TimeLimitState
    data class RunningOut(val remainingSeconds: Int) : TimeLimitState
    data object Locked : TimeLimitState
}

object TimeLimitPresenter {

    const val WARN_AT_SECONDS: Int = 300

    fun stateFor(remainingSeconds: Int, warnAtSeconds: Int = WARN_AT_SECONDS): TimeLimitState =
        when {
            remainingSeconds <= 0 -> TimeLimitState.Locked
            remainingSeconds <= warnAtSeconds -> TimeLimitState.RunningOut(remainingSeconds)
            else -> TimeLimitState.Plenty
        }
}
