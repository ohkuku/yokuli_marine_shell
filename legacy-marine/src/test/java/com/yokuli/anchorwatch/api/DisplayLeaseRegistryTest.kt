package com.yokuli.anchorwatch.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayLeaseRegistryTest {
    @Test fun releasingOneScreenKeepsTheOtherScreenActiveAndCloseIsIdempotent() {
        val transitions = mutableListOf<Boolean>()
        val registry = DisplayLeaseRegistry { transitions += it }
        val chart = registry.acquire()
        val secondActivity = registry.acquire()
        assertEquals(listOf(true), transitions)
        chart.close()
        chart.close()
        assertEquals(listOf(true), transitions)
        secondActivity.close()
        secondActivity.close()
        assertEquals(listOf(true, false), transitions)
    }

    @Test fun independentCapabilitiesAndReacquisitionRetainTheirOwnLifetime() {
        val transitions = mutableListOf<String>()
        val heading = DisplayLeaseRegistry { transitions += "heading:$it" }
        val instruments = DisplayLeaseRegistry { transitions += "instruments:$it" }
        val map = heading.acquire()
        val panel = instruments.acquire()
        map.close()
        assertEquals(listOf("heading:true", "instruments:true", "heading:false"), transitions)
        val nextMap = heading.acquire()
        panel.close()
        nextMap.close()
        assertEquals(listOf("heading:true", "instruments:true", "heading:false", "heading:true", "instruments:false", "heading:false"), transitions)
    }

    @Test fun failedActivationDoesNotCreateAnUnreleasableHolder() {
        var attempts = 0
        var active = false
        val registry = DisplayLeaseRegistry { enabled ->
            if (enabled && ++attempts == 1) error("Input not ready")
            active = enabled
        }
        assertTrue(runCatching { registry.acquire() }.isFailure)
        assertFalse(active)
        val retry = registry.acquire()
        assertTrue(active)
        retry.close()
        assertFalse(active)
        assertEquals(2, attempts)
    }
}
