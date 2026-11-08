package com.noam.kidtube.core

/**
 * Turns a video title into Hebrew, on the device.
 *
 * YouTube returns a creator's own Hebrew title where one exists, and for most English
 * children's channels none does. This is the fallback: a machine translation, computed
 * locally so nothing about what the child watches leaves the tablet.
 *
 * It is a port rather than a direct call so the rules around it - which titles to
 * translate, what to do when the model is missing, what to keep - are testable without a
 * device and without a 30MB model download.
 *
 * **No implementation ships in this build.** The wiring uses [None], and the port is what
 * makes that a one-line choice rather than a surgery - see its note for why, and for what
 * a replacement has to satisfy. An implementation must translate on the device: a title
 * is a record of what a child watches, and sending one to a service is the thing this app
 * exists to avoid.
 */
interface TitleTranslator {

    /**
     * Makes sure the language model is on the device, downloading it if not.
     *
     * False means translation is not available right now - no model and no way to fetch
     * one - and the caller should carry on with the original titles rather than wait.
     */
    suspend fun prepare(): Boolean

    /**
     * Hebrew for each title it managed. Titles it could not translate are simply absent
     * from the result; the caller keeps the original for those.
     */
    suspend fun translate(titles: List<String>): Map<String, String>

    /**
     * Whether this build has a translator at all.
     *
     * Distinct from [prepare], which answers whether a translator that *is* here can work
     * right now. False means no implementation was compiled in, so the parent screen
     * offers no switch to flip rather than a switch that silently does nothing.
     *
     * Defaulted so an implementation only states this if the answer is no.
     */
    val isInstalled: Boolean get() = true

    /**
     * What ships in the open-source build: no translator.
     *
     * The on-device translator used to be ML Kit, which is proprietary, and combining it
     * with this project's GPLv3 code made the APK undistributable - see the licence note
     * in the README. Removing it costs the fallback only: YouTube is still asked in
     * Hebrew, so a title the creator published in Hebrew still arrives in Hebrew. What is
     * gone is the machine translation of the ones they did not.
     *
     * Everything around it is kept deliberately. [TitleTranslationPass][1] and the
     * translation cache are untouched and still tested, so plugging a licence-compatible
     * translator in here is this one object's worth of change.
     *
     * [1]: com.noam.kidtube.app.sync.TitleTranslationPass
     */
    object None : TitleTranslator {
        override val isInstalled: Boolean get() = false
        override suspend fun prepare(): Boolean = false
        override suspend fun translate(titles: List<String>): Map<String, String> = emptyMap()
    }
}
