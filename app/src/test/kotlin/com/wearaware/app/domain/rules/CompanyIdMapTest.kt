package com.wearaware.app.domain.rules

import org.junit.Assert.*
import org.junit.Test

class CompanyIdMapTest {

    @Test
    fun `nameFor returns correct name for known company ID`() {
        assertEquals("Apple", CompanyIdMap.nameFor(0x004C))
        assertEquals("Meta", CompanyIdMap.nameFor(0x0075))
        assertEquals("Google", CompanyIdMap.nameFor(0x00E0))
    }

    @Test
    fun `nameFor returns formatted unknown string for unrecognised ID`() {
        val result = CompanyIdMap.nameFor(0x9999)
        assertTrue("Expected 'Unknown' prefix, got: $result", result.startsWith("Unknown"))
        assertTrue("Expected hex in result, got: $result", result.contains("9999", ignoreCase = true))
    }

    @Test
    fun `namesFor returns list of names for multiple IDs`() {
        val names = CompanyIdMap.namesFor(listOf(0x004C, 0x0075))
        assertEquals(listOf("Apple", "Meta"), names)
    }
}
