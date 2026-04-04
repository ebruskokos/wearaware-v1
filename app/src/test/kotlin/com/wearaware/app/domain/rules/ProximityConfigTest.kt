package com.wearaware.app.domain.rules

import com.wearaware.app.domain.model.ProximityLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class ProximityConfigTest {

    @Test
    fun `labelFromRssi returns VERY_CLOSE for signal at threshold`() {
        assertEquals(ProximityLabel.VERY_CLOSE, ProximityConfig.labelFromRssi(-55))
    }

    @Test
    fun `labelFromRssi returns VERY_CLOSE for signal stronger than threshold`() {
        assertEquals(ProximityLabel.VERY_CLOSE, ProximityConfig.labelFromRssi(-40))
    }

    @Test
    fun `labelFromRssi returns STRONG for signal at -65`() {
        assertEquals(ProximityLabel.STRONG, ProximityConfig.labelFromRssi(-65))
    }

    @Test
    fun `labelFromRssi returns STRONG for signal between -56 and -65`() {
        assertEquals(ProximityLabel.STRONG, ProximityConfig.labelFromRssi(-60))
    }

    @Test
    fun `labelFromRssi returns NEARBY for signal at -75`() {
        assertEquals(ProximityLabel.NEARBY, ProximityConfig.labelFromRssi(-75))
    }

    @Test
    fun `labelFromRssi returns WEAK for signal at -85`() {
        assertEquals(ProximityLabel.WEAK, ProximityConfig.labelFromRssi(-85))
    }

    @Test
    fun `labelFromRssi returns UNKNOWN for signal below -85`() {
        assertEquals(ProximityLabel.UNKNOWN, ProximityConfig.labelFromRssi(-90))
    }

    @Test
    fun `barCountFromLabel returns correct bar counts`() {
        assertEquals(5, ProximityConfig.barCountFromLabel(ProximityLabel.VERY_CLOSE))
        assertEquals(4, ProximityConfig.barCountFromLabel(ProximityLabel.STRONG))
        assertEquals(3, ProximityConfig.barCountFromLabel(ProximityLabel.NEARBY))
        assertEquals(2, ProximityConfig.barCountFromLabel(ProximityLabel.WEAK))
        assertEquals(1, ProximityConfig.barCountFromLabel(ProximityLabel.UNKNOWN))
    }
}
