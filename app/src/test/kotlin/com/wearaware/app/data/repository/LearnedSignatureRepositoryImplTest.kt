package com.wearaware.app.data.repository

import android.content.SharedPreferences
import com.google.gson.Gson
import com.wearaware.app.domain.model.LearnedDeviceSignature
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class LearnedSignatureRepositoryImplTest {

    private val prefs = mockk<SharedPreferences>()
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val gson = Gson()
    private lateinit var repo: LearnedSignatureRepositoryImpl

    private val testSignature = LearnedDeviceSignature(
        displayName = "My Meta Glasses",
        savedAt = 1_000_000L,
        fingerprintId = "fp-abc123",
        manufacturerIds = listOf(0x01AB),
        manufacturerDataPrefixes = listOf("01ab:deadbeef"),
        serviceUuids = listOf("0000fe2c-0000-1000-8000-00805f9b34fb")
    )

    @Before
    fun setUp() {
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor
        repo = LearnedSignatureRepositoryImpl(prefs, gson)
    }

    @Test
    fun `load returns null when key is missing`() {
        every { prefs.getString("learned_device_signature", null) } returns null
        assertNull(repo.load())
    }

    @Test
    fun `load returns null when JSON is malformed`() {
        every { prefs.getString("learned_device_signature", null) } returns "not-json"
        assertNull(repo.load())
    }

    @Test
    fun `save then load roundtrip returns identical signature`() {
        val stored = slot<String>()
        every { editor.putString("learned_device_signature", capture(stored)) } returns editor

        repo.save(testSignature)

        every { prefs.getString("learned_device_signature", null) } returns stored.captured
        val loaded = repo.load()

        assertEquals(testSignature, loaded)
    }

    @Test
    fun `clear removes the stored key`() {
        repo.clear()
        verify { editor.remove("learned_device_signature") }
        verify { editor.apply() }
    }

    @Test
    fun `save persists JSON and calls apply`() {
        repo.save(testSignature)
        verify { editor.putString("learned_device_signature", any()) }
        verify { editor.apply() }
    }
}
