package com.noam.kidtube.core.store

import kotlinx.serialization.Serializable

private const val FILE = "translations.json"

@Serializable
private data class TranslationState(val byOriginal: Map<String, String> = emptyMap())

/**
 * Titles already translated, keyed by the original.
 *
 * Translating is the expensive half of showing a Hebrew title, and a catalog changes
 * rarely, so each title is translated once and the answer kept. That is also what lets
 * the child's screen build its titles synchronously: the grid reads this map rather than
 * waiting on a translator while it is being laid out.
 *
 * Keyed by the title rather than by video id on purpose - the same title appears across
 * channels and re-uploads, and this way it is translated once for all of them.
 */
class TranslationStore(private val files: FileStore) {

    fun all(): Map<String, String> = load().byOriginal

    fun putAll(translations: Map<String, String>) {
        if (translations.isEmpty()) return
        write(TranslationState(load().byOriginal + translations))
    }

    /**
     * Which of [titles] have not been translated yet, each once.
     *
     * A title whose translation came back identical to the original still counts as
     * translated; otherwise a name or a bare number would be offered to the translator
     * again on every pass, forever.
     */
    fun missing(titles: Collection<String>): List<String> {
        val known = load().byOriginal
        return titles
            .asSequence()
            .filter { it.isNotBlank() }
            .distinct()
            .filterNot { known.containsKey(it) }
            .toList()
    }

    fun clear() {
        write(TranslationState())
    }

    private fun load(): TranslationState =
        files.read(FILE, TranslationState.serializer()) { TranslationState() }

    private fun write(state: TranslationState) {
        files.write(FILE, TranslationState.serializer(), state)
    }
}
