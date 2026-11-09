package com.pvorg.android.data

import java.io.File

/** An entry in a library folder. [location] is a URI string usable by the player and image loader. */
data class StoredEntry(
    val name: String,
    val isDirectory: Boolean,
    val location: String,
)

/**
 * The library root folder (spec §1.1: `pvorg.json`, `VID`, `VID/img`, `GAL`).
 * Paths are relative to the root, one element per folder level.
 */
interface LibraryStorage {
    val displayName: String

    /** Children of a folder, or `null` when it doesn't exist. */
    fun list(path: List<String>): List<StoredEntry>?

    /** A file's text, or `null` when it doesn't exist. */
    fun read(path: List<String>): String?

    /** Replaces (or creates) a file's text; atomic where the storage allows. */
    fun write(path: List<String>, text: String)

    /** Deletes a file; `false` when it couldn't be deleted. Missing files count as deleted. */
    fun delete(path: List<String>): Boolean

    /** Where the folder lives, for display in Settings. */
    val locationDescription: String
}

/** `java.io.File` storage: unit tests and the debug QA hook. */
class FileLibraryStorage(private val root: File) : LibraryStorage {
    override val displayName: String = root.name
    override val locationDescription: String = root.absolutePath

    private fun file(path: List<String>) = path.fold(root) { dir, part -> File(dir, part) }

    override fun list(path: List<String>): List<StoredEntry>? {
        val dir = file(path)
        if (!dir.isDirectory) return null
        return dir.listFiles().orEmpty().map { StoredEntry(it.name, it.isDirectory, it.toURI().toString()) }
    }

    override fun read(path: List<String>): String? = file(path).takeIf { it.isFile }?.readText()

    override fun write(path: List<String>, text: String) {
        val target = file(path)
        val temp = File(target.parentFile, ".${target.name}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            temp.delete()
            target.writeText(text)
        }
    }

    override fun delete(path: List<String>): Boolean {
        val target = file(path)
        return !target.exists() || target.delete()
    }
}
