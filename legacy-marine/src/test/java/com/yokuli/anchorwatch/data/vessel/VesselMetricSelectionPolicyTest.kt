package com.yokuli.anchorwatch.data.vessel

import com.yokuli.anchorwatch.domain.vessel.VesselMetricId
import com.yokuli.anchorwatch.domain.vessel.VesselSourcePreference
import org.junit.Assert.*
import org.junit.Test

class VesselMetricSelectionPolicyTest {
    private val legacy = VesselDataSettings(
        headingPreference = VesselSourcePreference.BOAT,
        boatHeadingSourceId = "IIHDT",
        metricSourcePins = mapOf("PRESSURE" to "phone:barometer", "HEADING_MAGNETIC" to "nmea:compass:HDM"),
    )

    @Test fun selectingPhoneHeadingDoesNotRewriteUnrelatedLegacyOrFieldChoices() {
        val chosen = VesselMetricSelectionPolicy.choose(legacy, VesselMetricId.HEADING_TRUE, "phone:vessel-heading")
        assertEquals("phone:vessel-heading", chosen.metricSourcePins["HEADING_TRUE"])
        assertEquals(legacy.boatHeadingSourceId, chosen.boatHeadingSourceId)
        assertEquals(legacy.headingPreference, chosen.headingPreference)
        assertEquals(legacy.metricSourcePins["HEADING_MAGNETIC"], chosen.metricSourcePins["HEADING_MAGNETIC"])
    }

    @Test fun explicitAutomaticHeadingClearsHiddenGroupPinAndPreservesOtherExplicitMetric() {
        val chosen = VesselMetricSelectionPolicy.choose(legacy.copy(metricSourcePins = legacy.metricSourcePins + ("HEADING_TRUE" to "phone:vessel-heading")), VesselMetricId.HEADING_TRUE, null)
        assertNull(chosen.boatHeadingSourceId)
        assertEquals(VesselSourcePreference.AUTO, chosen.headingPreference)
        assertFalse(chosen.metricSourcePins.containsKey("HEADING_TRUE"))
        assertEquals("nmea:compass:HDM", chosen.metricSourcePins["HEADING_MAGNETIC"])
        assertEquals("phone:barometer", chosen.metricSourcePins["PRESSURE"])
    }

    @Test fun AutomaticPressureDoesNotTouchLegacyHeading() {
        val chosen = VesselMetricSelectionPolicy.choose(legacy, VesselMetricId.PRESSURE, null)
        assertFalse(chosen.metricSourcePins.containsKey("PRESSURE"))
        assertEquals(legacy.headingPreference, chosen.headingPreference)
        assertEquals(legacy.boatHeadingSourceId, chosen.boatHeadingSourceId)
    }
}
