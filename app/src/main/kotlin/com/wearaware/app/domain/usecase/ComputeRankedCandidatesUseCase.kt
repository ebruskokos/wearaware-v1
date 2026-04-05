package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.CandidateLifecycle
import com.wearaware.app.domain.model.KnownMatchConfidence
import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.ObservedDevice
import com.wearaware.app.domain.model.RankedCandidate
import javax.inject.Inject

/**
 * Computes the stable top-N ranked list of candidate devices matched against the
 * learned [KnownTargetSignature].
 *
 * **Ranking stability** — a device can only move up in rank if its adjusted score
 * exceeds the device currently ahead of it by at least [REORDER_MARGIN]. This
 * prevents rapid rank-swapping from tick-to-tick score noise.
 *
 * **Primary lock** — once a device has been STRONG for [LOCK_THRESHOLD_MS] it is
 * pinned at rank #1. The lock is only broken if a challenger's score exceeds the
 * locked device's score by [LOCK_MARGIN].
 *
 * Pure function — all state is passed in; ScanViewModel owns the mutable maps.
 */
class ComputeRankedCandidatesUseCase @Inject constructor() {

    companion object {
        /** Minimum score difference required to displace a device from its current rank. */
        const val REORDER_MARGIN = 2
        /** Score advantage a challenger needs to displace a locked primary. */
        const val LOCK_MARGIN = 4
        /** How long (ms) a device must stay STRONG before it is locked as primary. */
        const val LOCK_THRESHOLD_MS = 10_000L
        /** Maximum number of ranked candidates to return. */
        const val MAX_CANDIDATES = 3
    }

    data class RankingInput(
        val device: ObservedDevice,
        val matchResult: KnownTargetMatchResult,
        val lifecycle: CandidateLifecycle
    )

    operator fun invoke(
        candidates: List<RankingInput>,
        prevRankOrder: List<String>,      // device IDs in previous stable order, #1 first
        primaryLockId: String?,
        nowMs: Long
    ): Pair<List<RankedCandidate>, String?> {  // (ranked list, updated primaryLockId)

        // Only include devices with a non-zero score
        val active = candidates.filter { it.matchResult.score > 0 }
        if (active.isEmpty()) return emptyList<RankedCandidate>() to null

        val scoreMap = active.associate { it.device.id to it.matchResult.score }
        val byId    = active.associateBy { it.device.id }

        // 1. Stable ranking via threshold-guarded bubble sort
        val stableOrder = buildStableOrder(scoreMap, prevRankOrder)

        // 2. Determine primary lock
        val newPrimaryLockId = resolvePrimaryLock(
            stableOrder, scoreMap, byId, primaryLockId, nowMs
        )

        // 3. Apply lock: if locked device is not already #1, check if challenger beats it by LOCK_MARGIN
        val finalOrder = applyPrimaryLock(stableOrder, scoreMap, newPrimaryLockId)

        // 4. Build RankedCandidate list
        val ranked = finalOrder.take(MAX_CANDIDATES).mapIndexedNotNull { index, id ->
            val input = byId[id] ?: return@mapIndexedNotNull null
            val rank = index + 1
            val isLocked = id == newPrimaryLockId
            val reason = buildRankReason(rank, input, isLocked, stableOrder, scoreMap, prevRankOrder)
            RankedCandidate(
                rank = rank,
                device = input.device,
                matchResult = input.matchResult,
                isPrimaryLock = isLocked,
                lifecycle = input.lifecycle,
                rankReason = reason
            )
        }

        return ranked to newPrimaryLockId
    }

    private fun buildStableOrder(
        scoreMap: Map<String, Int>,
        prevOrder: List<String>
    ): List<String> {
        // Start from previous order, keeping only devices still active; append new devices
        val order = prevOrder.filter { it in scoreMap }.toMutableList()
        scoreMap.keys.forEach { id -> if (id !in order) order.add(id) }

        // Threshold-guarded bubble sort: only swap if challenger leads by >= REORDER_MARGIN
        var changed = true
        while (changed) {
            changed = false
            for (i in 0 until order.size - 1) {
                val scoreA = scoreMap[order[i]] ?: 0
                val scoreB = scoreMap[order[i + 1]] ?: 0
                if (scoreB > scoreA + REORDER_MARGIN) {
                    val tmp = order[i]; order[i] = order[i + 1]; order[i + 1] = tmp
                    changed = true
                }
            }
        }
        return order
    }

    private fun resolvePrimaryLock(
        stableOrder: List<String>,
        scoreMap: Map<String, Int>,
        byId: Map<String, RankingInput>,
        currentLockId: String?,
        nowMs: Long
    ): String? {
        // Clear lock if locked device is no longer in active candidates
        if (currentLockId != null && currentLockId !in scoreMap) return null

        // Clear lock if locked device is no longer STRONG
        if (currentLockId != null) {
            val confidence = byId[currentLockId]?.matchResult?.confidence
            if (confidence == KnownMatchConfidence.NONE || confidence == KnownMatchConfidence.WEAK) {
                return null
            }
        }

        // Acquire lock: rank-1 device has been STRONG for >= LOCK_THRESHOLD_MS
        val rank1Id = stableOrder.firstOrNull()
        if (rank1Id != null && rank1Id != currentLockId) {
            val lifecycle = byId[rank1Id]?.lifecycle
            val strongSince = lifecycle?.strongSince
            if (strongSince != null && nowMs - strongSince >= LOCK_THRESHOLD_MS &&
                byId[rank1Id]?.matchResult?.confidence == KnownMatchConfidence.STRONG) {
                return rank1Id
            }
        }

        return currentLockId
    }

    private fun applyPrimaryLock(
        stableOrder: List<String>,
        scoreMap: Map<String, Int>,
        lockId: String?
    ): List<String> {
        if (lockId == null) return stableOrder
        if (stableOrder.firstOrNull() == lockId) return stableOrder

        val lockScore = scoreMap[lockId] ?: return stableOrder
        val rank1Score = scoreMap[stableOrder.firstOrNull()] ?: 0

        // Only unseat the lock if challenger exceeds it by LOCK_MARGIN
        return if (rank1Score > lockScore + LOCK_MARGIN) {
            stableOrder  // challenger wins — lock broken
        } else {
            // Keep lock at #1: move it to front, shift others down
            val reordered = stableOrder.toMutableList()
            reordered.remove(lockId)
            reordered.add(0, lockId)
            reordered
        }
    }

    private fun buildRankReason(
        rank: Int,
        input: RankingInput,
        isLocked: Boolean,
        stableOrder: List<String>,
        scoreMap: Map<String, Int>,
        prevOrder: List<String>
    ): String {
        val score = input.matchResult.score
        val confidence = input.matchResult.confidence.name
        val prevRank = prevOrder.indexOf(input.device.id).takeIf { it >= 0 }?.plus(1)

        return buildString {
            if (isLocked) append("Locked primary target. ")
            append("Score $score ($confidence)")
            if (prevRank != null && prevRank != rank) {
                append(" — moved from #$prevRank")
            }
            if (rank > 1) {
                val aboveId = stableOrder.getOrNull(rank - 2)
                val aboveScore = scoreMap[aboveId] ?: 0
                val gap = aboveScore - score
                if (gap > 0) append(" — $gap pts behind #${rank - 1}")
                if (gap in 1 until REORDER_MARGIN) append(" (stability hold)")
            }
            val lifecycle = input.lifecycle
            append(" — seen ${lifecycle.totalSeenCount} ticks, peak ${lifecycle.peakScore}")
        }
    }
}
