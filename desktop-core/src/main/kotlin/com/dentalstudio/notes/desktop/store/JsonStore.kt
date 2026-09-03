package com.dentalstudio.notes.desktop.store

import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

/**
 * Where the clinician's data lives on disk.
 *
 * Windows: %LOCALAPPDATA%\DentalNotes
 * macOS:   ~/Library/Application Support/DentalNotes
 * Linux:   $XDG_DATA_HOME/DentalNotes, or ~/.local/share/DentalNotes
 *
 * Deliberately outside any cloud-synced folder: these are clinical records.
 */
object AppPaths {

    val dataDir: Path by lazy {
        val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
        val base = when {
            os.contains("win") -> System.getenv("LOCALAPPDATA")
                ?: (System.getProperty("user.home") + "\\AppData\\Local")
            os.contains("mac") -> System.getProperty("user.home") + "/Library/Application Support"
            else -> System.getenv("XDG_DATA_HOME") ?: (System.getProperty("user.home") + "/.local/share")
        }
        Path.of(base, "DentalNotes").also { Files.createDirectories(it) }
    }

    val workspaceFile: Path get() = dataDir.resolve("workspace.json")
    val settingsFile: Path get() = dataDir.resolve("settings.json")
}

/**
 * Reads and writes one JSON file.
 *
 * Writes go to a temporary file first and are then moved into place, so a crash
 * mid-save cannot leave a half-written set of clinical notes behind. A file
 * that fails to parse is kept as .corrupt rather than being overwritten, so the
 * clinician still has something to recover from.
 */
class JsonStore<T>(
    private val path: Path,
    private val serializer: kotlinx.serialization.KSerializer<T>,
    private val empty: () -> T,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun load(): T {
        if (!Files.exists(path)) return empty()
        return try {
            json.decodeFromString(serializer, Files.readString(path))
        } catch (e: Exception) {
            quarantine(e)
            empty()
        }
    }

    fun save(value: T) {
        val temp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(temp, json.encodeToString(serializer, value))
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun quarantine(cause: Exception) {
        runCatching {
            val backup = path.resolveSibling(path.fileName.toString() + ".corrupt")
            Files.move(path, backup, StandardCopyOption.REPLACE_EXISTING)
            System.err.println("Could not read ${path.fileName}: ${cause.message}. Kept a copy at $backup.")
        }
    }
}
