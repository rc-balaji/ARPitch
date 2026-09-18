package com.laconfianza.arpitch.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchModelTest {
    @Test fun `22 yards is exact metric conversion`() {
        assertEquals(20.1168f, PitchUnits.yardsToMeters(22f), 0.00001f)
    }

    @Test fun `24 yards converts exactly`() {
        assertEquals(21.9456f, PitchUnits.yardsToMeters(24f), 0.00001f)
    }

    @Test fun `round trip meters yards`() {
        val meters = 13.42f
        val roundTrip = PitchUnits.yardsToMeters(PitchUnits.metersToYards(meters))
        assertEquals(meters, roundTrip, 0.00001f)
    }

    @Test fun `standard pitch identifies 22 yard preset`() {
        assertTrue(PitchSpec().isNearYards(22f))
    }
}
