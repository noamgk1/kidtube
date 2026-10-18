package com.noam.kidtube.core.store

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Every write goes to a sibling temp file and is then renamed over the target, so a
 * crash mid-write can never leave a truncated catalog: either the old file is intact or
 * the new one is complete.
 *
 * [onCorrupt] is how unreadable JSON reaches the parent's status screen instead of
 * crashing the app in a child's hands.
 */
class FileStore(
    private val root: File,
    private val onCorrupt: (relativePath: String, cause: Throwable) -> Unit = { _, _ -> },
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun <T> read(relativePath: String, serializer: KSerializer<T>, default: () -> T): T {
        val file = resolve(relativePath)
        if (!file.isFile) return default()
        return try {
            json.decodeFromString(serializer, file.readText())
        } catch (t: Throwable) {
            onCorrupt(relativePath, t)
            default()
        }
    }

    fun <T> write(relativePath: String, serializer: KSerializer<T>, value: T) {
        val target = resolve(relativePath)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(json.encodeToString(serializer, value))
        moveAtomically(temp, target)
    }

    fun appendLine(relativePath: String, line: String) {
        val target = resolve(relativePath)
        target.parentFile?.mkdirs()
        target.appendText(line + "\n")
    }

    fun readLines(relativePath: String): List<String> {
        val file = resolve(relativePath)
        if (!file.isFile) return emptyList()
        return file.readLines().filter { it.isNotBlank() }
    }

    fun replaceLines(relativePath: String, lines: List<String>) {
        val target = resolve(relativePath)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(lines.joinToString(separator = "\n", postfix = if (lines.isEmpty()) "" else "\n"))
        moveAtomically(temp, target)
    }

    fun delete(relativePath: String) {
        resolve(relativePath).delete()
    }

    fun exists(relativePath: String): Boolean = resolve(relativePath).exists()

    /** Temp files are hidden from listings so a half-finished write is never mistaken for data. */
    fun listNames(relativeDir: String): List<String> =
        resolve(relativeDir).listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".tmp") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    private fun resolve(relativePath: String): File = File(root, relativePath)

    private fun moveAtomically(from: File, to: File) {
        try {
            Files.move(
                from.toPath(),
                to.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
