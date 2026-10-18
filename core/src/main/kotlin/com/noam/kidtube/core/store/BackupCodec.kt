package com.noam.kidtube.core.store

import com.noam.kidtube.core.ChannelId
import com.noam.kidtube.core.VideoRef
import com.noam.kidtube.core.WatchEntry
import com.noam.kidtube.core.Whitelist
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Note what is absent: the password salt and hash. A parent who forgot the password
 * resets and restores; restoring the old hash would lock them straight back out.
 */
@Serializable
data class Backup(
    val version: Int = CURRENT_VERSION,
    val whitelist: Whitelist = Whitelist(),
    val catalogs: Map<String, List<VideoRef>> = emptyMap(),
    val dailyLimitMinutes: Int = 60,
    val engineId: String = "extractor",
    val history: List<WatchEntry> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}

class BackupCodec(
    private val whitelistStore: WhitelistStore,
    private val catalogStore: CatalogStore,
    private val settingsStore: SettingsStore,
    private val ledger: WatchLedger,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true }

    fun export(): String {
        val settings = settingsStore.load()
        val backup = Backup(
            whitelist = whitelistStore.load(),
            catalogs = catalogStore.channelIds().associate { it.value to catalogStore.load(it) },
            dailyLimitMinutes = settings.dailyLimitMinutes,
            engineId = settings.engineId,
            history = ledger.all(),
        )
        return json.encodeToString(Backup.serializer(), backup)
    }

    fun import(backupJson: String): Result<Unit> {
        val backup = runCatching { json.decodeFromString(Backup.serializer(), backupJson) }
            .getOrElse { return Result.failure(it) }

        if (backup.version > Backup.CURRENT_VERSION) {
            return Result.failure(
                IllegalArgumentException(
                    "backup version ${backup.version} is newer than this app understands " +
                        "(${Backup.CURRENT_VERSION}); update the app before importing",
                ),
            )
        }

        whitelistStore.save(backup.whitelist)
        backup.catalogs.forEach { (channelId, videos) ->
            catalogStore.save(ChannelId(channelId), videos)
        }
        // Password fields are preserved from whatever is installed right now.
        val current = settingsStore.load()
        settingsStore.save(
            current.copy(
                dailyLimitMinutes = backup.dailyLimitMinutes,
                engineId = backup.engineId,
            ),
        )
        backup.history.sortedBy { it.startedAtEpochSeconds }.forEach { ledger.append(it) }
        return Result.success(Unit)
    }
}
