package com.noam.kidtube.core

/**
 * Finds videos **within approved content**.
 *
 * This is deliberately not a YouTube search. It only ever receives videos already on the
 * whitelist, so it is a way to find something the parent has allowed, never a way to
 * reach anything else. Nothing here touches the network.
 *
 * The matching is built for Hebrew as much as for English: niqqud is stripped from both
 * sides, the geresh and the apostrophe are folded together, and final letter forms are
 * normalised, so a child typing plainly finds a title written carefully.
 */
object VideoSearch {

    /** A one-letter query must not try to render the whole library. */
    const val MAX_RESULTS: Int = 60

    private const val NIQQUD_START = '֑'
    private const val NIQQUD_END = 'ׇ'

    /** Geresh, gershayim and their typewriter lookalikes all fold to one form. */
    private val QUOTE_FORMS = setOf('׳', '״', '‘', '’', '“', '”', '\'', '"')

    /** Final forms folded to their ordinary counterparts, so a stem matches an inflection. */
    private val FINAL_FORMS = mapOf(
        'ך' to 'כ', // kaf
        'ם' to 'מ', // mem
        'ן' to 'נ', // nun
        'ף' to 'פ', // pe
        'ץ' to 'צ', // tsadi
    )

    fun search(videos: List<VideoRef>, query: String): List<VideoRef> {
        val terms = normalise(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()

        return videos
            .distinctBy { it.id.value }
            .mapNotNull { video ->
                val title = normalise(video.title)
                val score = scoreOf(title, terms) ?: return@mapNotNull null
                video to score
            }
            .sortedWith(
                compareBy<Pair<VideoRef, Int>> { it.second }
                    .thenByDescending { it.first.publishedAtEpochSeconds ?: 0L },
            )
            .take(MAX_RESULTS)
            .map { it.first }
    }

    /**
     * Lower is better. Every term must appear; the result is ranked by the best
     * position any term achieves, so a title that opens with the query comes first.
     */
    private fun scoreOf(title: String, terms: List<String>): Int? {
        var best = Int.MAX_VALUE
        for (term in terms) {
            val at = title.indexOf(term)
            if (at < 0) return null
            val rank = when {
                at == 0 -> 0
                title[at - 1] == ' ' -> 1
                else -> 2
            }
            if (rank < best) best = rank
        }
        return best
    }

    private fun normalise(raw: String): String {
        val folded = StringBuilder(raw.length)
        for (ch in raw.lowercase()) {
            when {
                ch in NIQQUD_START..NIQQUD_END -> Unit // drop vowel points entirely
                ch in QUOTE_FORMS -> Unit
                ch.isLetterOrDigit() -> folded.append(FINAL_FORMS[ch] ?: ch)
                else -> folded.append(' ')
            }
        }
        return folded.toString().split(' ').filter { it.isNotBlank() }.joinToString(" ")
    }
}
