package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.KnownTargetSignature
import com.wearaware.app.domain.model.SignatureSnapshot

/**
 * Single-record store for the user's learned glasses signature, plus a bounded
 * history of prior versions for export and rollback.
 *
 * [load] returns null both when no signature has been saved and when the persisted data is corrupt.
 * [save] is synchronous (commit) — caller can rely on persistence surviving an immediate process kill.
 * [saveWithHistory] is like [save] but also pushes a [SignatureSnapshot] onto the history ring buffer.
 * [loadHistory] returns snapshots newest-first; empty when none have been recorded.
 * [rollbackTo] restores a prior snapshot by version and returns it, or null if not found.
 * [clear] removes the current signature and history.
 */
interface KnownTargetRepository {
    fun load(): KnownTargetSignature?
    fun save(signature: KnownTargetSignature)
    fun saveWithHistory(signature: KnownTargetSignature, deltaDescription: String)
    fun loadHistory(): List<SignatureSnapshot>
    fun rollbackTo(version: Int): KnownTargetSignature?
    fun clear()
}
