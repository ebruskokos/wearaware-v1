package com.wearaware.app.domain.rules

import org.junit.Assert.*
import org.junit.Test

class RssiSmootherTest {

    @Test
    fun `single reading returns that reading`() {
        val smoother = RssiSmoother(windowSize = 5)
        assertEquals(-60, smoother.addReading(-60))
    }

    @Test
    fun `averages two readings correctly`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.addReading(-60)
        val result = smoother.addReading(-70)
        assertEquals(-65, result)
    }

    @Test
    fun `averages four readings correctly`() {
        val smoother = RssiSmoother(windowSize = 4)
        smoother.addReading(-60)
        smoother.addReading(-70)
        smoother.addReading(-80)
        val result = smoother.addReading(-90)
        // (-60 + -70 + -80 + -90) / 4 = -75
        assertEquals(-75, result)
    }

    @Test
    fun `drops oldest reading when window is full`() {
        val smoother = RssiSmoother(windowSize = 3)
        smoother.addReading(-60)
        smoother.addReading(-60)
        smoother.addReading(-60)
        // window full: [-60, -60, -60]
        val result = smoother.addReading(-90)
        // drops first -60, window is [-60, -60, -90]
        // (-60 + -60 + -90) / 3 = -70
        assertEquals(-70, result)
    }

    @Test
    fun `reset clears all readings`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.addReading(-60)
        smoother.addReading(-70)
        smoother.reset()
        assertNull(smoother.currentAverage())
    }

    @Test
    fun `after reset addReading starts fresh`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.addReading(-60)
        smoother.reset()
        assertEquals(-80, smoother.addReading(-80))
    }

    @Test
    fun `currentAverage returns null when empty`() {
        val smoother = RssiSmoother(windowSize = 5)
        assertNull(smoother.currentAverage())
    }

    @Test
    fun `currentAverage returns current average without adding reading`() {
        val smoother = RssiSmoother(windowSize = 5)
        smoother.addReading(-60)
        smoother.addReading(-80)
        assertEquals(-70, smoother.currentAverage())
    }
}
