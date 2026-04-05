package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.wearaware.app.domain.model.*
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class KnownTargetRepositoryImplTest {

    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val gson = Gson()
    private lateinit var repo: KnownTargetRepositoryImpl

    private val testSignature = KnownTargetSignature(
        displayName = "My Meta Glasses",
        savedAt = 1_000_000L,
        fingerprintId = "fp-abc123",
        manufacturerIds = listOf(0x0075),
        manufacturerDataPrefixes = listOf("0075:deadbeef"),
        serviceUuids = listOf("0000fe2c-0000-1000-8000-00805f9b34fb"),
        gattServiceUuids = listOf("0000180a-0000-1000-8000-00805f9b34fb"),
        behaviorProfile = KnownBehaviorProfile(typicalRssiAtClose = -55, minSeenCount = 80)
    )

    @Before
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        repo = KnownTargetRepositoryImpl(prefs, gson)
    }

    @Test
    fun `load returns null when key is missing`() {
        every { prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) } returns null
        assertNull(repo.load())
    }

    @Test
    fun `load returns null when JSON is malformed`() {
        every { prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) } returns "not-json"
        assertNull(repo.load())
    }

    @Test
    fun `save then load roundtrip returns identical signature`() {
        val stored = slot<String>()
        every { editor.putString(KEY_KNOWN_TARGET_SIGNATURE, capture(stored)) } returns editor
        repo.save(testSignature)
        every { prefs.getString(KEY_KNOWN_TARGET_SIGNATURE, null) } returns stored.captured
        assertEquals(testSignature, repo.load())
    }

    @Test
    fun `save commits synchronously`() {
        repo.save(testSignature)
        verify { editor.putString(KEY_KNOWN_TARGET_SIGNATURE, any()) }
        verify { editor.commit() }
    }

    @Test
    fun `clear removes the stored key`() {
        repo.clear()
        verify { editor.remove(KEY_KNOWN_TARGET_SIGNATURE) }
        verify { editor.apply() }
    }
}
