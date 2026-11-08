package com.noam.kidtube.app.admin

import com.noam.kidtube.core.PasswordHasher
import com.noam.kidtube.core.Whitelist
import com.noam.kidtube.core.store.CatalogStore
import com.noam.kidtube.core.store.SettingsStore
import com.noam.kidtube.core.store.WatchLedger
import com.noam.kidtube.core.store.WhitelistStore

/**
 * The admin door ships locked, with [DEFAULT_PASSWORD] already installed. That is what
 * closes the one hole a first-run prompt cannot: on a device where no password has been
 * chosen yet, whoever reaches the admin hold first gets to choose it - parent or child.
 * A parent changes it from the settings tab; a child never gets the chance to set it.
 *
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

    /**
     * Installs the factory password, but only when none is set. Called once at startup;
     * overwriting on every launch would quietly undo the parent's own password.
     */
    fun ensureDefaultPassword() {
        if (!isPasswordSet()) setPassword(DEFAULT_PASSWORD.toCharArray())
    }

    fun setPassword(password: CharArray) {
        settingsStore.save(PasswordHasher.install(settingsStore.load(), password, iterations))
    }

    fun authenticate(password: CharArray): Boolean =
        PasswordHasher.verify(settingsStore.load(), password, iterations)

    fun resetDestructively() {
        catalogStore.channelIds().forEach { catalogStore.delete(it) }
        whitelistStore.save(Whitelist())
        ledger.rotate(maxEntries = 0, maxAgeDays = 0)
        // Back to the factory password rather than to none: a reset must not be a way to
        // leave the door standing open.
        settingsStore.save(PasswordHasher.clear(settingsStore.load()))
        ensureDefaultPassword()
    }

    companion object {
        /** What the door is locked with until a parent changes it. */
        const val DEFAULT_PASSWORD: String = "parent1234"

        /** Short enough to type on a tablet, long enough not to be guessed by a child. */
        const val MIN_PASSWORD_LENGTH: Int = 4
    }
}
