package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.DeviceTemporalState
import com.wearaware.app.domain.model.DeviceTemporalState.Companion.RSSI_WINDOW
import com.wearaware.app.domain.model.DeviceTemporalState.Companion.SCORE_WINDOW
import com.wearaware.app.domain.model.KnownMatchConfidence
import com.wearaware.app.domain.model.KnownTargetMatchResult
import com.wearaware.app.domain.model.VisibilityState
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Wraps a raw [KnownTargetMatchResult] with temporal stability logic:
 *
 * 1. **Anti-spike RSSI filtering** — maintains a rolling RSSI window (7 samples).
 *    Low variance (σ < 5 dBm) → +1 score bonus; high variance (σ > 15 dBm) → -1 penalty.
 *
 * 2. **Score smoothing** — rolling average of last 5 raw scores replaces instantaneous score.
 *    Prevents single-tick jumps.
 *
 * 3. **Confidence decay** — if device visibility is SIGNAL_LOST, penalises by staleness:
 *    5–10 s → -1 | 10–20 s → -2 | 20+ s → -4.
 *
 * 4. **Temporal stability gate** — STRONG requires smoothed score ≥ 8 AND ≥ 3 consecutive
 *    ticks with score ≥ 4. POSSIBLE requires ≥ 2 consecutive ticks.
 *
 * 5. **Hysteresis** — enter STRONG at smoothedScore ≥ 8; only leave STRONG at < 6.
 *    Enter POSSIBLE at ≥ 4; only leave POSSIBLE at < 3.
 *
 * Pure function — all state is passed in and returned in [TemporalFilterResult].
 */
class ApplyTemporalMatchFilterUseCase @Inject constructor() {

    // Hysteresis thresholds
    private val STRONG_ENTER = 8
    private val STRONG_STAY  = 6
    private val POSSIBLE_ENTER = 4
    private val POSSIBLE_STAY  = 3
    private val STABILITY_FOR_STRONG = 3   // ticks
    private val STABILITY_FOR_POSSIBLE = 2 // ticks

    data class TemporalFilterResult(
        val adjustedResult: KnownTargetMatchResult,
        val updatedState: DeviceTemporalState
    )

    operator fun invoke(
        rawResult: KnownTargetMatchResult,
        state: DeviceTemporalState,
        currentRssi: Int,
        visibilityState: VisibilityState,
        lastSeenAtMs: Long,
        nowMs: Long
    ): TemporalFilterResult {
        val notes = mutableListOf<String>()

        // 1. Update RSSI history and compute variance
        val rssiHistory = (state.rssiHistory + currentRssi).takeLast(RSSI_WINDOW)
        val rssiMean = rssiHistory.average()
        val rssiVariance = rssiHistory.map { v -> (v - rssiMean).let { it * it } }.average()
        val rssiStdDev = sqrt(rssiVariance)
        val varianceAdjustment = when {
            rssiStdDev < 5.0  -> { notes += "Low RSSI variance σ=${rssiStdDev.fmt()} (+1)"; +1 }
            rssiStdDev > 15.0 -> { notes += "High RSSI variance σ=${rssiStdDev.fmt()} (-1)"; -1 }
            else -> 0
        }

        // 2. Update score history and compute smoothed score
        val scoreHistory = (state.scoreHistory + rawResult.score).takeLast(SCORE_WINDOW)
        val smoothedScore = scoreHistory.average().roundToInt() + varianceAdjustment

        // 3. Confidence decay based on staleness
        val stalenessMs = nowMs - lastSeenAtMs
        val decay = if (visibilityState == VisibilityState.SIGNAL_LOST) {
            when {
                stalenessMs >= 20_000 -> { notes += "Stale 20+ s (-4)"; -4 }
                stalenessMs >= 10_000 -> { notes += "Stale 10–20 s (-2)"; -2 }
                stalenessMs >= 5_000  -> { notes += "Stale 5–10 s (-1)"; -1 }
                else -> 0
            }
        } else 0
        val decayedScore = smoothedScore + decay

        // 4. Stability tracking
        val newStableCount = if (smoothedScore >= POSSIBLE_ENTER)
            state.stableObservationCount + 1
        else 0
        val prev = state.prevConfidence

        // 5. Confidence classification with hysteresis + stability gate
        val newConfidence = when {
            // Require structural evidence for STRONG/POSSIBLE
            !rawResult.hasStructuralSignal ->
                if (decayedScore >= 2) KnownMatchConfidence.WEAK else KnownMatchConfidence.NONE

            // STRONG — enter at 8 with stability, stay at 6
            decayedScore >= STRONG_ENTER && newStableCount >= STABILITY_FOR_STRONG ->
                KnownMatchConfidence.STRONG

            prev == KnownMatchConfidence.STRONG && decayedScore >= STRONG_STAY ->
                KnownMatchConfidence.STRONG.also { notes += "Hysteresis: staying STRONG" }

            // POSSIBLE — enter at 4 with stability, stay at 3
            decayedScore >= POSSIBLE_ENTER && newStableCount >= STABILITY_FOR_POSSIBLE ->
                KnownMatchConfidence.POSSIBLE

            prev == KnownMatchConfidence.POSSIBLE && decayedScore >= POSSIBLE_STAY ->
                KnownMatchConfidence.POSSIBLE.also { notes += "Hysteresis: staying POSSIBLE" }

            decayedScore >= 2 -> KnownMatchConfidence.WEAK
            else -> KnownMatchConfidence.NONE
        }

        if (smoothedScore != rawResult.score) {
            notes += "Score smoothed: raw=${rawResult.score} → smoothed=$smoothedScore (window=${scoreHistory.size})"
        }
        if (newStableCount < STABILITY_FOR_STRONG &&
            newConfidence != KnownMatchConfidence.STRONG &&
            rawResult.confidence == KnownMatchConfidence.STRONG) {
            notes += "Stability gate: raw=STRONG held at $newStableCount/${STABILITY_FOR_STRONG} ticks"
        }

        val labelOverride = newConfidence == KnownMatchConfidence.STRONG ||
            newConfidence == KnownMatchConfidence.POSSIBLE

        val adjustedResult = rawResult.copy(
            confidence = newConfidence,
            labelOverrideActive = labelOverride,
            temporalNotes = notes
        )

        val updatedState = state.copy(
            rssiHistory = rssiHistory,
            scoreHistory = scoreHistory,
            prevConfidence = newConfidence,
            stableObservationCount = newStableCount
        )

        return TemporalFilterResult(adjustedResult, updatedState)
    }

    private fun Double.fmt() = "%.1f".format(this)
}
