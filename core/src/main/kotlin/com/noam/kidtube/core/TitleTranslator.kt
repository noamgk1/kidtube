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
}
