package com.noam.kidtube.app.admin

import com.noam.kidtube.core.PasswordHasher
import com.noam.kidtube.core.Whitelist
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore

/**
 * There is no password recovery, deliberately: a back door for a parent is a back door
 * for a child. [resetDestructively] is the only way back in, and it takes the whitelist,
 * the blocklist and the history with it - which is exactly what makes it useless as a
 * child's shortcut. The parent's safety net is an exported backup, which carries no
 * password.
 *
 * The daily limit survives, so a parent who resets does not have to reconfigure
 * everything - and keeping it hands a child nothing.
 */
class AdminAuthenticator(
    private val settingsStore: SettingsStore,
    private val whitelistStore: WhitelistStore,
    private val catalogStore: CatalogStore,
    private val ledger: WatchLedger,
    private val iterations: Int = PasswordHasher.ITERATIONS,
) {
    fun isPasswordSet(): Boolean = settingsStore.load().passwordHashBase64 != null

    fun setPassword(password: CharArray) {
        settingsStore.save(PasswordHasher.install(settingsStore.load(), password, iterations))
    }

    fun authenticate(password: CharArray): Boolean =
        PasswordHasher.verify(settingsStore.load(), password, iterations)

    fun resetDestructively() {
        catalogStore.channelIds().forEach { catalogStore.delete(it) }
        whitelistStore.save(Whitelist())
        ledger.rotate(maxEntries = 0, maxAgeDays = 0)
        settingsStore.save(PasswordHasher.clear(settingsStore.load()))
    }
}
