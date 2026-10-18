package com.noam.kidtube.core.store

import com.noam.kidtube.core.Settings

private const val FILE = "settings.json"

class SettingsStore(private val files: FileStore) {
    fun load(): Settings = files.read(FILE, Settings.serializer()) { Settings() }
    fun save(settings: Settings) = files.write(FILE, Settings.serializer(), settings)
}
