package com.noam.kidtube.app.admin

import com.noam.kidtube.core.SyncState
import com.noam.kidtube.core.store.BackupCodec
import com.noam.kidtube.core.store.SyncStateStore
import com.noam.kidtube.engine.EngineHealth
import kotlinx.coroutines.flow.StateFlow

data class StatusView(
    val engineHealth: EngineHealth,
    val incompleteSyncs: List<SyncState>,
    val corruptions: List<String>,
)

/** Everything the child was shielded from, shown to the parent in full. */
class StatusPresenter(
    private val engineHealth: StateFlow<EngineHealth>,
    private val syncStateStore: SyncStateStore,
    private val backupCodec: BackupCodec,
    private val corruptions: List<String>,
) {
    fun view(): StatusView = StatusView(
        engineHealth = engineHealth.value,
        incompleteSyncs = syncStateStore.all().filter { !it.complete || it.lastError != null },
        // Copied, not aliased: the caller holds a snapshot that will not shift underneath
        // a composition.
        corruptions = corruptions.toList(),
    )

    fun exportBackup(): String = backupCodec.export()

    fun importBackup(json: String): Result<Unit> = backupCodec.import(json)
}
