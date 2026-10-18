package com.noam.kidtube.core.store

import com.noam.kidtube.core.Clock
import com.noam.kidtube.core.WatchAggregate
import com.noam.kidtube.core.WatchEntry
import kotlinx.serialization.json.Json

private const val FILE = "history.jsonl"

/**
 * Append-only, one JSON object per line: the cheapest and most crash-safe shape for a
 * log, and a malformed line costs one entry rather than the whole history.
 */
class WatchLedger(
    private val files: FileStore,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun append(entry: WatchEntry) {
        files.appendLine(FILE, json.encodeToString(WatchEntry.serializer(), entry))
    }

    /** Newest first. */
    fun all(): List<WatchEntry> =
        files.readLines(FILE)
            .mapNotNull { line ->
                runCatching { json.decodeFromString(WatchEntry.serializer(), line) }.getOrNull()
            }
            .sortedByDescending { it.startedAtEpochSeconds }

    fun aggregate(): List<WatchAggregate> =
        all().groupBy { it.videoId }
            .map { (videoId, entries) ->
                WatchAggregate(
                    videoId = videoId,
                    title = entries.first().title,
                    plays = entries.size,
                    totalSeconds = entries.sumOf { it.watchedSeconds },
                    lastAtEpochSeconds = entries.maxOf { it.startedAtEpochSeconds },
                )
            }
            .sortedByDescending { it.lastAtEpochSeconds }

    /** Trims by age first, then by count, keeping the newest. Rewritten atomically. */
    fun rotate(maxEntries: Int = MAX_ENTRIES, maxAgeDays: Long = MAX_AGE_DAYS) {
        val entries = all()
        if (entries.isEmpty()) return

        val cutoff = clock.nowEpochMillis() / 1_000 - maxAgeDays * 86_400L
        val kept = entries
            .filter { it.startedAtEpochSeconds >= cutoff }
            .take(maxEntries)

        if (kept.size == entries.size) return

        // Stored oldest-first so appends keep working naturally.
        files.replaceLines(
            FILE,
            kept.sortedBy { it.startedAtEpochSeconds }
                .map { json.encodeToString(WatchEntry.serializer(), it) },
        )
    }

    companion object {
        const val MAX_ENTRIES: Int = 5_000
        const val MAX_AGE_DAYS: Long = 90
    }
}
