package com.wearaware.app.domain.model

/**
 * A device in the ranked candidate list, with its temporally-adjusted match result
 * and lifecycle data.
 */
data class RankedCandidate(
    /** 1-based rank position. */
    val rank: Int,
    val device: ObservedDevice,
    val matchResult: KnownTargetMatchResult,
    /** True when this device has been locked as primary target. */
    val isPrimaryLock: Boolean,
    val lifecycle: CandidateLifecycle,
    /** Human-readable explanation of why this device holds this rank. */
    val rankReason: String
)
