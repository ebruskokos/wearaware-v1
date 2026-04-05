package com.wearaware.app.domain.usecase

import com.wearaware.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MatchLearnedSignatureUseCaseTest {

    private val useCase = MatchLearnedSignatureUseCase()

    private fun makeSignature(
        fingerprintId: String = "saved-fp",
        manufacturerIds: List<Int> = listOf(0x01AB),
        prefixes: List<String> = listOf("01ab:deadbeef"),
        serviceUuids: List<String> = listOf("uuid-glasses")
    ) = LearnedDeviceSignature(
        displayName = "My Meta Glasses",
        savedAt = 1000L,
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids
    )

    private fun makeInput(
        fingerprintId: String = "candidate-fp",
        manufacturerIds: List<Int> = emptyList(),
        prefixes: List<String> = emptyList(),
        serviceUuids: List<String> = emptyList(),
        averageRssi: Int = -80,
        seenCount: Int = 1,
        visibleAtStop: Boolean = false
    ) = LearnedMatchInput(
        fingerprintId = fingerprintId,
        manufacturerIds = manufacturerIds,
        manufacturerDataPrefixes = prefixes,
        serviceUuids = serviceUuids,
        averageRssi = averageRssi,
        seenCount = seenCount,
        visibleAtStop = visibleAtStop
    )

    @Test
    fun `manufacturer data prefix + manufacturer ID overlap produces STRONG with label override`() {
        val result = useCase(
            makeInput(
                manufacturerIds = listOf(0x01AB),
                prefixes = listOf("01ab:deadbeef")
            ),
            makeSignature()
        )
        // +5 prefix + +4 mfrId = 9 → STRONG
        assertEquals(LearnedConfidence.STRONG, result.confidence)
        assertTrue(result.labelOverrideActive)
        assertTrue(result.score >= 8)
    }

    @Test
    fun `manufacturer ID overlap only produces POSSIBLE`() {
        val result = useCase(
            makeInput(manufacturerIds = listOf(0x01AB)),
            makeSignature(prefixes = emptyList())
        )
        // +4 mfrId only = 4 → POSSIBLE
        assertEquals(LearnedConfidence.POSSIBLE, result.confidence)
        assertTrue(result.labelOverrideActive)
    }

    @Test
    fun `no signal overlap produces NONE with no label override`() {
        val result = useCase(
            makeInput(
                manufacturerIds = listOf(0x004C),
                prefixes = listOf("004c:aabbccdd")
            ),
            makeSignature()
        )
        assertEquals(LearnedConfidence.NONE, result.confidence)
        assertFalse(result.labelOverrideActive)
    }

    @Test
    fun `fingerprintId exact match alone does not reach STRONG`() {
        val result = useCase(
            makeInput(fingerprintId = "saved-fp"),
            makeSignature(manufacturerIds = emptyList(), prefixes = emptyList(), serviceUuids = emptyList())
        )
        // +3 fingerprintId only = 3 → NONE (below 4)
        assertEquals(LearnedConfidence.NONE, result.confidence)
    }

    @Test
    fun `behavior signals contribute but cannot alone reach STRONG`() {
        val result = useCase(
            makeInput(
                manufacturerIds = emptyList(),
                prefixes = emptyList(),
                serviceUuids = emptyList(),
                averageRssi = -55,
                seenCount = 60,
                visibleAtStop = true
            ),
            makeSignature(manufacturerIds = emptyList(), prefixes = emptyList(), serviceUuids = emptyList())
        )
        // +2 +2 +1 = 5 → POSSIBLE (not STRONG)
        assertEquals(LearnedConfidence.POSSIBLE, result.confidence)
        assertFalse(result.score >= 8)
    }

    @Test
    fun `service UUID overlap adds to score`() {
        val result = useCase(
            makeInput(serviceUuids = listOf("uuid-glasses", "uuid-other")),
            makeSignature(manufacturerIds = emptyList(), prefixes = emptyList(), serviceUuids = listOf("uuid-glasses"))
        )
        // +2 UUID overlap = 2
        assertTrue(result.score >= 2)
    }

    @Test
    fun `fingerprintId match is included in signals list`() {
        val result = useCase(
            makeInput(fingerprintId = "saved-fp"),
            makeSignature()
        )
        assertTrue(result.matchedSignals.any { it.contains("FingerprintId") })
    }

    @Test
    fun `STRONG confidence full overlap has correct displayName`() {
        val sig = makeSignature()
        val result = useCase(
            makeInput(
                fingerprintId = "saved-fp",
                manufacturerIds = listOf(0x01AB),
                prefixes = listOf("01ab:deadbeef"),
                serviceUuids = listOf("uuid-glasses"),
                averageRssi = -55,
                seenCount = 60,
                visibleAtStop = true
            ),
            sig
        )
        assertEquals("My Meta Glasses", result.signature.displayName)
        assertEquals(LearnedConfidence.STRONG, result.confidence)
        assertTrue(result.labelOverrideActive)
    }
}
