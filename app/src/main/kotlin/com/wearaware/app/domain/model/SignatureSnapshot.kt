package com.wearaware.app.domain.model

/**
 * Immutable snapshot of a [KnownTargetSignature] at the moment it was saved.
 * The full signature is preserved to allow rollback to any prior version.
 * Stored as a JSON array via [com.wearaware.app.domain.repository.KnownTargetRepository].
 * At most [com.wearaware.app.data.repository.KnownTargetRepositoryImpl.MAX_HISTORY] entries are kept.
 */
data class SignatureSnapshot(
    val version: Int,
    val savedAt: Long,
    /** Short description of what changed in this version; empty string for initial saves. */
    val deltaDescription: String,
    val signature: KnownTargetSignature
)
