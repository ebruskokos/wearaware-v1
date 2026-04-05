package com.wearaware.app.domain.repository

import com.wearaware.app.domain.model.KnownTargetSignature

/**
 * Single-record store for the user's learned glasses signature.
 * At most one [KnownTargetSignature] exists at any time.
 *
 * [load] returns null both when no signature has been saved and when the persisted data is corrupt.
 * [save] is synchronous (commit) — caller can rely on persistence surviving an immediate process kill.
 * [clear] is asynchronous (apply).
 */
interface KnownTargetRepository {
    fun load(): KnownTargetSignature?
    fun save(signature: KnownTargetSignature)
    fun clear()
}
