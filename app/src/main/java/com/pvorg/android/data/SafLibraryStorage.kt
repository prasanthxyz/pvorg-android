package com.pvorg.android.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * Storage Access Framework storage for a folder the user picked. Listings use
 * one `DocumentsContract` query per folder (DocumentFile issues several per
 * child), and document IDs are cached so lookups don't re-walk the tree.
 */
class SafLibraryStorage(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
) : LibraryStorage {
    private val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    private val ids = mutableMapOf<List<String>, String>(emptyList<String>() to rootId)
    private val directories = mutableSetOf<List<String>>(emptyList())

    override val displayName: String = rootId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Library" }
    override val locationDescription: String = rootId.substringAfter(':').ifEmpty { displayName }

    private fun documentUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private fun idFor(path: List<String>): String? {
        ids[path]?.let { return it }
        if (path.isEmpty()) return rootId
        val parent = path.dropLast(1)
        list(parent) ?: return null
        return ids[path]
    }

    @Synchronized
    override fun list(path: List<String>): List<StoredEntry>? {
        val id = idFor(path) ?: return null
        if (path.isNotEmpty() && path !in directories) return null
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE)
        val entries = mutableListOf<StoredEntry>()
        resolver.query(children, columns, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val isDirectory = cursor.getString(2) == Document.MIME_TYPE_DIR
                val childPath = path + name
                ids[childPath] = childId
                if (isDirectory) directories += childPath else directories -= childPath
                entries += StoredEntry(name, isDirectory, documentUri(childId).toString())
            }
        } ?: return null
        return entries
    }

    override fun read(path: List<String>): String? {
        val id = idFor(path) ?: return null
        if (path in directories) return null
        return resolver.openInputStream(documentUri(id))?.bufferedReader()?.use { it.readText() }
    }

    /** Providers can't rename over an existing document, so this is a full rewrite (spec §15.1). */
    override fun write(path: List<String>, text: String) {
        val id = idFor(path) ?: run {
            val parentId = idFor(path.dropLast(1)) ?: error("Missing folder: ${path.dropLast(1).joinToString("/")}")
            val created = DocumentsContract.createDocument(resolver, documentUri(parentId), "application/json", path.last())
                ?: error("Couldn’t create ${path.last()}")
            DocumentsContract.getDocumentId(created).also { ids[path] = it }
        }
        resolver.openOutputStream(documentUri(id), "wt")?.bufferedWriter()?.use { it.write(text) }
            ?: error("Couldn’t write ${path.last()}")
    }

    @Synchronized
    override fun delete(path: List<String>): Boolean {
        val id = idFor(path) ?: return true
        val deleted = runCatching { DocumentsContract.deleteDocument(resolver, documentUri(id)) }.getOrDefault(false)
        if (deleted) ids -= path
        return deleted
    }
}
