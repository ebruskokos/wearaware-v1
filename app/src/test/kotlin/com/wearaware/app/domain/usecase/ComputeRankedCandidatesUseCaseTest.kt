package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import com.wearaware.app.domain.usecase.ComputeRankedCandidatesUseCase.RankingInput
import org.junit.Assert.*
import org.junit.Test

class ComputeRankedCandidatesUseCaseTest {

    private val useCase = ComputeRankedCandidatesUseCase()
    private val now = 200_000L

    private fun makeSig() = KnownTargetSignature(
        displayName = "My Glasses", savedAt = 0L, fingerprintId = "fp",
        manufacturerIds = listOf(0x0075), manufacturerDataPrefixes = emptyList(),
        serviceUuids = emptyList(), gattServiceUuids = emptyList(), behaviorProfile = null
    )

    private fun makeResult(score: Int, confidence: KnownMatchConfidence = KnownMatchConfidence.POSSIBLE) =
        KnownTargetMatchResult(
            signature = makeSig(), score = score, confidence = confidence,
            matchedSignals = emptyList(),
            labelOverrideActive = confidence != KnownMatchConfidence.NONE && confidence != KnownMatchConfidence.WEAK,
            hasStructuralSignal = score > 0
        )

    private fun makeDevice(id: String) = ObservedDevice(
        id = id, advertisedName = "Device $id", rawRssi = -65, averagedRssi = -65,
        proximityLabel = ProximityLabel.NEARBY, visibilityState = VisibilityState.DETECTED_NOW,
        firstSeenAt = 0L, lastSeenAt = now, seenCount = 10,
        classification = ClassificationResult(
            matchedRuleId = null, ruleVersion = null,
            category = DeviceCategory.UNKNOWN_BLE_DEVICE,
            displayLabel = "Unknown", confidence = ConfidenceLevel.LOW,
            isWearableCandidate = false, evaluationNotes = null
        ),
        persistenceAlert = null
    )

    private fun makeLifecycle(
        strongSince: Long? = null,
        totalSeenCount: Int = 1,
        peakScore: Int = 5
    ) = CandidateLifecycle(
        discoveredAt = 0L,
        lastSeenAt = now,
        peakScore = peakScore,
        totalSeenCount = totalSeenCount,
        strongSince = strongSince
    )

    private fun input(id: String, score: Int,
                      confidence: KnownMatchConfidence = KnownMatchConfidence.POSSIBLE,
                      strongSince: Long? = null
    ) = RankingInput(makeDevice(id), makeResult(score, confidence), makeLifecycle(strongSince = strongSince, peakScore = score))

    // --- Basic ranking ---

    @Test
    fun `devices are ranked by score descending`() {
        val (ranked, _) = useCase(
            listOf(input("A", 5), input("B", 10), input("C", 3)),
            emptyList(), null, now
        )
        assertEquals("B", ranked[0].device.id)
        assertEquals("A", ranked[1].device.id)
        assertEquals("C", ranked[2].device.id)
    }

    @Test
    fun `only top MAX_CANDIDATES returned`() {
        val inputs = (1..6).map { input("dev$it", it * 2) }
        val (ranked, _) = useCase(inputs, emptyList(), null, now)
        assertEquals(ComputeRankedCandidatesUseCase.MAX_CANDIDATES, ranked.size)
    }

    @Test
    fun `devices with score 0 are excluded`() {
        val (ranked, _) = useCase(
            listOf(input("A", 0), input("B", 5), input("C", 0)),
            emptyList(), null, now
        )
        assertEquals(1, ranked.size)
        assertEquals("B", ranked[0].device.id)
    }

    @Test
    fun `empty candidates returns empty list`() {
        val (ranked, lockId) = useCase(emptyList(), emptyList(), null, now)
        assertTrue(ranked.isEmpty())
        assertNull(lockId)
    }

    @Test
    fun `rank numbers are 1-based and sequential`() {
        val (ranked, _) = useCase(
            listOf(input("A", 10), input("B", 7), input("C", 4)),
            emptyList(), null, now
        )
        assertEquals(1, ranked[0].rank)
        assertEquals(2, ranked[1].rank)
        assertEquals(3, ranked[2].rank)
    }

    // --- Ranking stability ---

    @Test
    fun `device does not displace rank-1 if score advantage is below REORDER_MARGIN`() {
        // A is currently rank-1 with score 10; B has score 11 (advantage = 1 < REORDER_MARGIN=2)
        val prevOrder = listOf("A", "B")
        val (ranked, _) = useCase(
            listOf(input("A", 10), input("B", 11)),
            prevOrder, null, now
        )
        // B's advantage (1) < REORDER_MARGIN (2) → A stays at #1
        assertEquals("A", ranked[0].device.id)
    }

    @Test
    fun `device displaces rank-1 when advantage exceeds REORDER_MARGIN`() {
        val prevOrder = listOf("A", "B")
        val (ranked, _) = useCase(
            listOf(input("A", 8), input("B", 11)),  // diff = 3 > REORDER_MARGIN(2)
            prevOrder, null, now
        )
        // B's advantage (3) > REORDER_MARGIN (2) → allowed to swap
        assertEquals("B", ranked[0].device.id)
    }

    @Test
    fun `device at exactly REORDER_MARGIN difference does not displace`() {
        val prevOrder = listOf("A", "B")
        val (ranked, _) = useCase(
            listOf(input("A", 8), input("B", 10)),  // diff = 2 == REORDER_MARGIN (not >)
            prevOrder, null, now
        )
        // Condition is strict >, so equal margin does not trigger swap
        assertEquals("A", ranked[0].device.id)
    }

    @Test
    fun `new device entering the list is appended after stable devices`() {
        val prevOrder = listOf("A")
        val (ranked, _) = useCase(
            listOf(input("A", 8), input("NewDevice", 7)),
            prevOrder, null, now
        )
        // NewDevice is new — starts at the back, can't displace A (diff=1 < REORDER_MARGIN)
        assertEquals("A", ranked[0].device.id)
        assertEquals("NewDevice", ranked[1].device.id)
    }

    @Test
    fun `device removed from candidates is removed from ranking`() {
        val prevOrder = listOf("A", "B", "C")
        // C is gone (score 0 = excluded)
        val (ranked, _) = useCase(
            listOf(input("A", 10), input("B", 8)),
            prevOrder, null, now
        )
        assertFalse(ranked.any { it.device.id == "C" })
    }

    // --- Primary lock ---

    @Test
    fun `device becomes locked after STRONG for LOCK_THRESHOLD_MS`() {
        val strongSince = now - ComputeRankedCandidatesUseCase.LOCK_THRESHOLD_MS - 1
        val (ranked, lockId) = useCase(
            listOf(input("A", 11, KnownMatchConfidence.STRONG, strongSince = strongSince)),
            listOf("A"), null, now
        )
        assertEquals("A", lockId)
        assertTrue(ranked[0].isPrimaryLock)
    }

    @Test
    fun `device does not lock before LOCK_THRESHOLD_MS elapsed`() {
        val strongSince = now - ComputeRankedCandidatesUseCase.LOCK_THRESHOLD_MS + 1_000
        val (_, lockId) = useCase(
            listOf(input("A", 11, KnownMatchConfidence.STRONG, strongSince = strongSince)),
            listOf("A"), null, now
        )
        assertNull(lockId)
    }

    @Test
    fun `locked device stays rank-1 when challenger lacks LOCK_MARGIN advantage`() {
        val strongSince = now - ComputeRankedCandidatesUseCase.LOCK_THRESHOLD_MS - 1
        // A is locked; B has score=A+LOCK_MARGIN-1 → not enough to unseat
        val lockMarginShort = ComputeRankedCandidatesUseCase.LOCK_MARGIN - 1
        val (ranked, _) = useCase(
            listOf(
                input("A", 10, KnownMatchConfidence.STRONG, strongSince = strongSince),
                input("B", 10 + lockMarginShort)
            ),
            listOf("A", "B"), "A", now
        )
        assertEquals("A", ranked[0].device.id)
        assertTrue(ranked[0].isPrimaryLock)
    }

    @Test
    fun `locked device is unseated when challenger exceeds LOCK_MARGIN`() {
        val strongSince = now - ComputeRankedCandidatesUseCase.LOCK_THRESHOLD_MS - 1
        val lockBreakScore = 10 + ComputeRankedCandidatesUseCase.LOCK_MARGIN + 1
        val (ranked, _) = useCase(
            listOf(
                input("A", 10, KnownMatchConfidence.STRONG, strongSince = strongSince),
                input("B", lockBreakScore)
            ),
            listOf("A", "B"), "A", now
        )
        // B's advantage > LOCK_MARGIN → lock is broken, B goes to rank #1
        assertEquals("B", ranked[0].device.id)
    }

    @Test
    fun `lock is cleared when locked device drops below POSSIBLE`() {
        val (_, lockId) = useCase(
            listOf(input("A", 2, KnownMatchConfidence.WEAK)),
            listOf("A"), "A", now
        )
        // A's confidence is WEAK → lock cleared
        assertNull(lockId)
    }

    @Test
    fun `lock is cleared when locked device leaves candidates`() {
        val (_, lockId) = useCase(
            listOf(input("B", 8)),   // A is gone
            listOf("B"), "A", now
        )
        assertNull(lockId)
    }

    // --- Lifecycle in ranked output ---

    @Test
    fun `ranked candidate exposes correct lifecycle data`() {
        val lifecycle = CandidateLifecycle(
            discoveredAt = 1000L, lastSeenAt = now,
            peakScore = 12, totalSeenCount = 42
        )
        val inputs = listOf(RankingInput(makeDevice("A"), makeResult(10), lifecycle))
        val (ranked, _) = useCase(inputs, emptyList(), null, now)
        assertEquals(12, ranked[0].lifecycle.peakScore)
        assertEquals(42, ranked[0].lifecycle.totalSeenCount)
    }

    // --- Rank reason ---

    @Test
    fun `rank reason is non-empty for all candidates`() {
        val (ranked, _) = useCase(
            listOf(input("A", 10), input("B", 7), input("C", 4)),
            emptyList(), null, now
        )
        ranked.forEach { assertTrue(it.rankReason.isNotBlank()) }
    }

    @Test
    fun `rank reason mentions lock for locked primary`() {
        val strongSince = now - ComputeRankedCandidatesUseCase.LOCK_THRESHOLD_MS - 1
        val (ranked, _) = useCase(
            listOf(input("A", 11, KnownMatchConfidence.STRONG, strongSince)),
            listOf("A"), null, now
        )
        if (ranked[0].isPrimaryLock) {
            assertTrue(ranked[0].rankReason.contains("Locked", ignoreCase = true))
        }
    }
}
