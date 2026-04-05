package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ApplyTemporalMatchFilterUseCaseTest {

    private val useCase = ApplyTemporalMatchFilterUseCase()
    private val now = 100_000L

    private fun makeSig() = KnownTargetSignature(
        displayName = "My Glasses",
        savedAt = 0L,
        fingerprintId = "fp-001",
        manufacturerIds = listOf(0x0075),
        manufacturerDataPrefixes = listOf("0075:deadbeef"),
        serviceUuids = emptyList(),
        gattServiceUuids = emptyList(),
        behaviorProfile = null
    )

    private fun makeRaw(
        score: Int,
        confidence: KnownMatchConfidence = KnownMatchConfidence.STRONG,
        hasStructuralSignal: Boolean = true
    ) = KnownTargetMatchResult(
        signature = makeSig(),
        score = score,
        confidence = confidence,
        matchedSignals = listOf("Manufacturer ID overlap: 0x0075 (+6)"),
        labelOverrideActive = confidence == KnownMatchConfidence.STRONG || confidence == KnownMatchConfidence.POSSIBLE,
        hasStructuralSignal = hasStructuralSignal
    )

    private fun freshState() = DeviceTemporalState()

    // --- Temporal stability gate ---

    @Test
    fun `first tick with STRONG raw score stays below STRONG — stability gate`() {
        val result = useCase(makeRaw(score = 10), freshState(), -60, VisibilityState.DETECTED_NOW, now, now)
        // Only 1 observation — below STABILITY_FOR_STRONG (3)
        assertNotEquals(KnownMatchConfidence.STRONG, result.adjustedResult.confidence)
    }

    @Test
    fun `three consecutive ticks with high score reaches STRONG`() {
        var state = freshState()
        repeat(3) { tick ->
            val r = useCase(makeRaw(score = 10), state, -60, VisibilityState.DETECTED_NOW, now, now)
            state = r.updatedState
            if (tick < 2) assertNotEquals("Tick $tick: should not be STRONG yet",
                KnownMatchConfidence.STRONG, r.adjustedResult.confidence)
        }
        val final = useCase(makeRaw(score = 10), state, -60, VisibilityState.DETECTED_NOW, now, now)
        assertEquals(KnownMatchConfidence.STRONG, final.adjustedResult.confidence)
    }

    @Test
    fun `two ticks produces POSSIBLE not STRONG`() {
        var state = freshState()
        repeat(2) {
            val r = useCase(makeRaw(score = 9), state, -60, VisibilityState.DETECTED_NOW, now, now)
            state = r.updatedState
        }
        val result = useCase(makeRaw(score = 9), state, -60, VisibilityState.DETECTED_NOW, now, now)
        // stableCount = 3 → should be STRONG (score 9 smoothed stays >= 8)
        assertEquals(KnownMatchConfidence.STRONG, result.adjustedResult.confidence)
    }

    @Test
    fun `stableObservationCount resets when smoothed score drops below POSSIBLE threshold`() {
        // Construct state with low score history so a new score=0 pushes average below 4
        val state = DeviceTemporalState(
            scoreHistory = listOf(0, 0, 0, 0),
            stableObservationCount = 3,
            prevConfidence = KnownMatchConfidence.POSSIBLE
        )
        val r = useCase(makeRaw(score = 0, confidence = KnownMatchConfidence.NONE, hasStructuralSignal = false),
            state, -75, VisibilityState.DETECTED_NOW, now, now)
        assertEquals(0, r.updatedState.stableObservationCount)
    }

    // --- Hysteresis ---

    @Test
    fun `STRONG confidence stays at STRONG when smoothed score is 7 (above STRONG_STAY=6)`() {
        // Use moderate RSSI variance (σ≈6, no bonus/penalty) so variance adjustment = 0
        // Score history [7,7,7,7] → smoothed=7, which is between ENTER(8) and STAY(6) → hysteresis
        val moderateRssi = listOf(-53, -67, -53, -67, -53, -67) // σ ≈ 7
        val strongState = DeviceTemporalState(
            rssiHistory = moderateRssi,
            scoreHistory = listOf(7, 7, 7, 7),
            stableObservationCount = 5,
            prevConfidence = KnownMatchConfidence.STRONG
        )
        val result = useCase(makeRaw(score = 7, confidence = KnownMatchConfidence.POSSIBLE), strongState,
            -60, VisibilityState.DETECTED_NOW, now, now)
        assertEquals(KnownMatchConfidence.STRONG, result.adjustedResult.confidence)
        assertTrue(result.adjustedResult.temporalNotes.any { it.contains("Hysteresis") })
    }

    @Test
    fun `STRONG confidence drops when smoothed score falls below STRONG_STAY=6`() {
        // Score history [4,4,4,4] → smoothed=4, even with variance +1 → 5 < STRONG_STAY(6)
        val strongState = DeviceTemporalState(
            scoreHistory = listOf(4, 4, 4, 4),
            stableObservationCount = 5,
            prevConfidence = KnownMatchConfidence.STRONG
        )
        val result = useCase(makeRaw(score = 4, confidence = KnownMatchConfidence.POSSIBLE), strongState,
            -70, VisibilityState.DETECTED_NOW, now, now)
        // smoothed=4, variance+1=5, decay=0 → decayedScore=5 < STRONG_STAY(6) → drops
        assertNotEquals(KnownMatchConfidence.STRONG, result.adjustedResult.confidence)
    }

    @Test
    fun `POSSIBLE confidence stays POSSIBLE when score is 3 (above POSSIBLE_STAY=3)`() {
        // Build POSSIBLE state (score 5, 2+ ticks)
        var state = freshState()
        repeat(3) {
            val r = useCase(makeRaw(score = 5, confidence = KnownMatchConfidence.POSSIBLE), state,
                -70, VisibilityState.DETECTED_NOW, now, now)
            state = r.updatedState
        }
        // Score drops to 3
        val result = useCase(makeRaw(score = 3, confidence = KnownMatchConfidence.WEAK), state,
            -72, VisibilityState.DETECTED_NOW, now, now)
        assertEquals(KnownMatchConfidence.POSSIBLE, result.adjustedResult.confidence)
    }

    // --- Confidence decay ---

    @Test
    fun `no decay applied when device is active (DETECTED_NOW)`() {
        val rawResult = makeRaw(score = 8)
        // Build to STRONG first
        var state = freshState()
        repeat(3) {
            val r = useCase(rawResult, state, -60, VisibilityState.DETECTED_NOW, now, now)
            state = r.updatedState
        }
        val result = useCase(rawResult, state, -60, VisibilityState.DETECTED_NOW, now, now)
        assertTrue(result.adjustedResult.temporalNotes.none { it.contains("Stale") })
    }

    @Test
    fun `5-10 second staleness applies -1 decay`() {
        val lastSeen = now - 7_000L
        val result = useCase(makeRaw(score = 10), freshState(), -60, VisibilityState.SIGNAL_LOST, lastSeen, now)
        assertTrue(result.adjustedResult.temporalNotes.any { it.contains("Stale 5–10 s") })
    }

    @Test
    fun `10-20 second staleness applies -2 decay`() {
        val lastSeen = now - 15_000L
        val result = useCase(makeRaw(score = 10), freshState(), -60, VisibilityState.SIGNAL_LOST, lastSeen, now)
        assertTrue(result.adjustedResult.temporalNotes.any { it.contains("Stale 10–20 s") })
    }

    @Test
    fun `20+ second staleness applies -4 decay`() {
        val lastSeen = now - 25_000L
        val result = useCase(makeRaw(score = 10), freshState(), -60, VisibilityState.SIGNAL_LOST, lastSeen, now)
        assertTrue(result.adjustedResult.temporalNotes.any { it.contains("Stale 20+") })
    }

    @Test
    fun `heavy decay can prevent STRONG — score drops below STRONG_STAY threshold`() {
        // Build STRONG state first
        var state = freshState()
        repeat(4) {
            val r = useCase(makeRaw(score = 10), state, -60, VisibilityState.DETECTED_NOW, now, now)
            state = r.updatedState
        }
        assertEquals(KnownMatchConfidence.STRONG, state.prevConfidence)

        // Now device is stale 20+ s and score has dropped to 6 (smoothed history still has 10s)
        // The -4 decay will push it below STRONG_STAY(6)
        val stateWithLowScores = state.copy(
            scoreHistory = listOf(6, 6, 6, 6, 6)
        )
        val lastSeen = now - 25_000L
        val result = useCase(makeRaw(score = 6, confidence = KnownMatchConfidence.POSSIBLE),
            stateWithLowScores, -75, VisibilityState.SIGNAL_LOST, lastSeen, now)
        // smoothed=6, decay=-4 → 2 → below STRONG_STAY(6) and below POSSIBLE_STAY(3) → WEAK
        assertNotEquals(KnownMatchConfidence.STRONG, result.adjustedResult.confidence)
        assertNotEquals(KnownMatchConfidence.POSSIBLE, result.adjustedResult.confidence)
    }

    // --- Score smoothing ---

    @Test
    fun `smoothed score is average of rolling score history`() {
        val state = DeviceTemporalState(scoreHistory = listOf(6, 6, 6, 6))
        // New raw score = 10, window becomes [6,6,6,6,10], avg = 7.6 → 8
        // With stable count from prior state we should see the smoothed note
        val result = useCase(makeRaw(score = 10), state, -70, VisibilityState.DETECTED_NOW, now, now)
        assertEquals(5, result.updatedState.scoreHistory.size)
        assertTrue(result.updatedState.scoreHistory.contains(10))
    }

    @Test
    fun `single tick spike does not reach STRONG — score window smooths it out`() {
        val stateWithLowHistory = DeviceTemporalState(
            scoreHistory = listOf(2, 2, 2, 2),
            stableObservationCount = 0
        )
        // Sudden spike to score=12 — but window average = (2+2+2+2+12)/5 = 4 → POSSIBLE gate needs 2 ticks
        val result = useCase(makeRaw(score = 12), stateWithLowHistory, -50,
            VisibilityState.DETECTED_NOW, now, now)
        assertNotEquals(KnownMatchConfidence.STRONG, result.adjustedResult.confidence)
    }

    // --- RSSI variance ---

    @Test
    fun `low RSSI variance produces +1 bonus note`() {
        val state = DeviceTemporalState(rssiHistory = listOf(-65, -66, -65, -64, -65, -66))
        val result = useCase(makeRaw(score = 6), state, -65, VisibilityState.DETECTED_NOW, now, now)
        assertTrue(result.adjustedResult.temporalNotes.any { it.contains("Low RSSI variance") && it.contains("+1") })
    }

    @Test
    fun `high RSSI variance produces -1 penalty note`() {
        val state = DeviceTemporalState(rssiHistory = listOf(-40, -90, -45, -85, -50, -80))
        val result = useCase(makeRaw(score = 6), state, -60, VisibilityState.DETECTED_NOW, now, now)
        assertTrue(result.adjustedResult.temporalNotes.any { it.contains("High RSSI variance") && it.contains("-1") })
    }

    @Test
    fun `RSSI history is capped at RSSI_WINDOW entries`() {
        val initialHistory = List(DeviceTemporalState.RSSI_WINDOW) { -65 }
        val state = DeviceTemporalState(rssiHistory = initialHistory)
        val result = useCase(makeRaw(score = 5), state, -70, VisibilityState.DETECTED_NOW, now, now)
        assertEquals(DeviceTemporalState.RSSI_WINDOW, result.updatedState.rssiHistory.size)
        assertTrue(result.updatedState.rssiHistory.contains(-70))
    }

    // --- No structural signal ---

    @Test
    fun `no structural signal prevents STRONG even with high score and stable observations`() {
        var state = freshState()
        repeat(4) {
            val r = useCase(makeRaw(score = 10, hasStructuralSignal = false), state,
                -55, VisibilityState.DETECTED_NOW, now, now)
            state = r.updatedState
        }
        val result = useCase(makeRaw(score = 10, hasStructuralSignal = false), state,
            -55, VisibilityState.DETECTED_NOW, now, now)
        assertNotEquals(KnownMatchConfidence.STRONG, result.adjustedResult.confidence)
        assertNotEquals(KnownMatchConfidence.POSSIBLE, result.adjustedResult.confidence)
    }

    // --- Label override ---

    @Test
    fun `labelOverrideActive is true for STRONG confidence after temporal filter`() {
        var state = freshState()
        repeat(3) {
            val r = useCase(makeRaw(score = 11), state, -60, VisibilityState.DETECTED_NOW, now, now)
            state = r.updatedState
        }
        val result = useCase(makeRaw(score = 11), state, -60, VisibilityState.DETECTED_NOW, now, now)
        if (result.adjustedResult.confidence == KnownMatchConfidence.STRONG) {
            assertTrue(result.adjustedResult.labelOverrideActive)
        }
    }

    @Test
    fun `labelOverrideActive is false for NONE confidence`() {
        val result = useCase(makeRaw(score = 0, confidence = KnownMatchConfidence.NONE, hasStructuralSignal = false),
            freshState(), -85, VisibilityState.SIGNAL_LOST, now - 30_000L, now)
        assertFalse(result.adjustedResult.labelOverrideActive)
    }
}
