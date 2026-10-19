package com.noam.kidtube.app.translate

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.noam.kidtube.core.TitleTranslator
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * On-device machine translation of video titles, English to Hebrew.
 *
 * Nothing leaves the tablet: ML Kit downloads a language model once and translates
 * locally afterwards, so the titles a child sees - and therefore what they watch - are
 * never sent anywhere. The download is the only network call, and it is deliberately
 * held to unmetered connections.
 *
 * The translation itself is machine translation of short, punctuation-heavy strings, and
 * it reads like it. It is the fallback for titles whose creator never published a Hebrew
 * one; where YouTube has the creator's own translation, that is what arrives and this is
 * never asked.
 */
class MlKitTitleTranslator(
    private val options: TranslatorOptions = TranslatorOptions.Builder()
        .setSourceLanguage(TranslateLanguage.ENGLISH)
        .setTargetLanguage(TranslateLanguage.HEBREW)
        .build(),
) : TitleTranslator {

    // Created lazily and kept: building one is cheap, but closing and re-opening it
    // around every batch is not.
    private val translator: Translator by lazy { Translation.getClient(options) }

    @Volatile
    private var modelReady = false

    override suspend fun prepare(): Boolean {
        if (modelReady) return true
        // Unmetered only. A 30MB model pulled over a phone's data allowance because a
        // child opened the app is not a trade a parent agreed to.
        val conditions = DownloadConditions.Builder().requireWifi().build()
        val downloaded = runCatching {
            translator.downloadModelIfNeeded(conditions).await()
        }.isSuccess
        modelReady = downloaded
        return downloaded
    }

    override suspend fun translate(titles: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        titles.forEach { title ->
            // One failure is one title left in its original language, not a lost batch.
            runCatching { translator.translate(title).await() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { result[title] = it }
        }
        return result
    }

    fun close() {
        translator.close()
    }
}

/** Bridges a Play-services Task into a coroutine, cancellation included. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> continuation.resumeIfActive(value) }
    addOnFailureListener { cause ->
        if (continuation.isActive) continuation.resumeWith(Result.failure(cause))
    }
    addOnCanceledListener { continuation.cancel() }
}

private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
    if (isActive) resume(value)
}
