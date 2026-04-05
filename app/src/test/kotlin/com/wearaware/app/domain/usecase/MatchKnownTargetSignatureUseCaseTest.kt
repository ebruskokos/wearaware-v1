package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MatchKnownTargetSignatureUseCaseTest {

    private val useCase = MatchKnownTargetSignatureUseCase()

    private fun makeSignature(
        fingerprintId: String = "saved-fp",
        manufacturerIds: List<Int> = listOf(0x0075),
        prefixes: List<String> = listOf("0075:deadbeef"),
        serviceUuids: List<String> = listOf("uuid-glasses"),
        gattServiceUuids: List<String> = listOf("gatt-service-1")
    ) = KnownTargetSignature(
        displayName = "My Meta Glasses",
        savedAt = 1000L,
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids,
        gattServiceUuids = gattServiceUuids,
        behaviorProfile = null
    )

    private fun makeInput(
        fingerprintId: String = "candidate-fp",
        manufacturerIds: List<Int> = emptyList(),
        prefixes: List<String> = emptyList(),
        serviceUuids: List<String> = emptyList(),
        gattServiceUuids: List<String> = emptyList(),
        averageRssi: Int = -75,
        seenCount: Int = 10,
        visibleAtStop: Boolean = false,
        connectable: Boolean = false
    ) = KnownTargetMatchInput(
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids,
        gattServiceUuids = gattServiceUuids,
        averageRssi = averageRssi,
        seenCount = seenCount,
        visibleAtStop = visibleAtStop,
        connectable = connectable
    )

    @Test
    fun `mfr data prefix + mfr ID + GATT service produces STRONG`() {
        // +5 prefix + +6 mfrId + +3 gatt = 14 → STRONG
        val result = useCase(
            makeInput(
                manufacturerIds = listOf(0x0075),
                prefixes = listOf("0075:deadbeef"),
                gattServiceUuids = listOf("gatt-service-1")
            ),
            makeSignature()
        )
        assertEquals(KnownMatchConfidence.STRONG, result.confidence)
        assertTrue(result.labelOverrideActive)
        assertTrue(result.score >= 12)
    }

    @Test
    fun `mfr ID only with high persistence produces POSSIBLE`() {
        // +6 mfrId + +3 seenCount = 9 → POSSIBLE (not STRONG: <12)
        val result = useCase(
            makeInput(manufacturerIds = listOf(0x0075), seenCount = 60),
            makeSignature()
        )
        assertEquals(KnownMatchConfidence.POSSIBLE, result.confidence)
        assertTrue(result.labelOverrideActive)
    }

    @Test
    fun `mfr ID only at low persistence produces WEAK`() {
        // fingerprint match only → +3 = WEAK
        val result = useCase(
            makeInput(fingerprintId = "saved-fp"),
            makeSignature(fingerprintId = "saved-fp", manufacturerIds = listOf(0x9999), prefixes = emptyList(), serviceUuids = emptyList(), gattServiceUuids = emptyList())
        )
        assertEquals(KnownMatchConfidence.WEAK, result.confidence)
        assertFalse(result.labelOverrideActive)
    }

    @Test
    fun `no matching signals produces NONE`() {
        val result = useCase(
            makeInput(),  // empty, rssi -75, seenCount 10
            makeSignature()
        )
        assertEquals(KnownMatchConfidence.NONE, result.confidence)
        assertFalse(result.labelOverrideActive)
        assertEquals(0, result.score)
    }

    @Test
    fun `Apple-only manufacturer ID applies penalty`() {
        // +6 apple mfr id match - 5 apple penalty = 1 → NONE
        val result = useCase(
            makeInput(manufacturerIds = listOf(0x004C)),
            makeSignature(manufacturerIds = listOf(0x004C), prefixes = emptyList(), serviceUuids = emptyList(), gattServiceUuids = emptyList())
        )
        assertTrue("Apple penalty should reduce score", result.score < 6)
    }

    @Test
    fun `very weak signal applies rssi penalty`() {
        // rssi = -85 → -4 penalty applied
        val result = useCase(
            makeInput(averageRssi = -85),
            makeSignature()
        )
        val resultNoPenalty = useCase(
            makeInput(averageRssi = -75),
            makeSignature()
        )
        assertTrue(result.score < resultNoPenalty.score)
    }

    @Test
    fun `very low seenCount applies persistence penalty`() {
        // seenCount = 3 → -3 penalty
        val result = useCase(
            makeInput(seenCount = 3),
            makeSignature()
        )
        val resultNoPenalty = useCase(
            makeInput(seenCount = 10),
            makeSignature()
        )
        assertTrue(result.score < resultNoPenalty.score)
    }

    @Test
    fun `visibleAtStop and connectable each add bonus`() {
        val base = useCase(makeInput(), makeSignature())
        val withBonuses = useCase(
            makeInput(visibleAtStop = true, connectable = true),
            makeSignature()
        )
        assertEquals(4, withBonuses.score - base.score)
    }

    @Test
    fun `matched signals list is populated for each scoring contribution`() {
        val result = useCase(
            makeInput(
                manufacturerIds = listOf(0x0075),
                prefixes = listOf("0075:deadbeef"),
                gattServiceUuids = listOf("gatt-service-1"),
                seenCount = 60
            ),
            makeSignature()
        )
        assertTrue(result.matchedSignals.any { it.contains("Manufacturer") })
        assertTrue(result.matchedSignals.any { it.contains("prefix") || it.contains("Prefix") })
        assertTrue(result.matchedSignals.any { it.contains("GATT") || it.contains("gatt") })
        assertTrue(result.matchedSignals.any { it.contains("persistence") || it.contains("Persistence") || it.contains("50") })
    }
}
