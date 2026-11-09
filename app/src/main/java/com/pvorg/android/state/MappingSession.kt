package com.pvorg.android.state

import androidx.compose.runtime.Immutable
import com.pvorg.android.domain.Library
import com.pvorg.android.domain.MappingKind
import com.pvorg.android.domain.MappingRow
import com.pvorg.android.domain.TriState

/**
 * Unsaved edits and view options for one mapping matrix (spec §6.3). Lives in
 * the view model, so leaving the screen never discards pending work.
 */
@Immutable
data class MappingSession(
    val kind: MappingKind,
    /** filename → column → new state, only for cells that differ from what is saved. */
    val pending: Map<String, Map<String, TriState>> = emptyMap(),
    val showsDecided: Boolean = false,
    val query: String = "",
) {
    val changeCount: Int get() = pending.values.sumOf { it.size }
    val hasChanges: Boolean get() = pending.isNotEmpty()

    fun state(row: MappingRow, column: String): TriState =
        pending[row.video.filename]?.get(column) ?: row.saved[column] ?: TriState.UNDECIDED

    fun isModified(row: MappingRow, column: String): Boolean = pending[row.video.filename]?.containsKey(column) == true

    fun cycling(row: MappingRow, column: String): MappingSession = setting(state(row, column).next, row, column)

    fun setting(state: TriState, row: MappingRow, column: String): MappingSession {
        val saved = row.saved[column] ?: TriState.UNDECIDED
        val entry = pending[row.video.filename].orEmpty().toMutableMap()
        if (state == saved) entry.remove(column) else entry[column] = state
        val updated = pending.toMutableMap()
        if (entry.isEmpty()) updated.remove(row.video.filename) else updated[row.video.filename] = entry
        return copy(pending = updated)
    }

    /** Applies [state] to one column for every shown row. */
    fun settingColumn(state: TriState, column: String, rows: List<MappingRow>): MappingSession =
        rows.fold(this) { session, row -> session.setting(state, row, column) }

    /** Applies [state] to every column of one row. */
    fun settingRow(state: TriState, row: MappingRow, columns: List<String>): MappingSession =
        columns.fold(this) { session, column -> session.setting(state, row, column) }

    fun discarding(filename: String, column: String): MappingSession {
        val entry = pending[filename]?.minus(column) ?: return this
        return copy(pending = if (entry.isEmpty()) pending - filename else pending + (filename to entry))
    }

    /** Drops changes for videos or columns that no longer exist. */
    fun pruned(library: Library): MappingSession {
        val videos = library.videos.map { it.filename }.toSet()
        val columns = library.columns(kind).toSet()
        val kept = pending.mapNotNull { (filename, cells) ->
            if (filename !in videos) return@mapNotNull null
            cells.filterKeys { it in columns }.takeIf { it.isNotEmpty() }?.let { filename to it }
        }.toMap()
        return if (kept == pending) this else copy(pending = kept)
    }

    fun reverted(): MappingSession = copy(pending = emptyMap())
}

/** The Library selection only ever contains videos that are shown (spec §3.8). */
object LibrarySelection {
    fun pruned(selection: Set<String>, visible: Collection<String>): Set<String> = selection.intersect(visible.toSet())
}
