package com.yokuli.marine.data.source

import com.yokuli.marine.data.model.ConnectionId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarineFeatureLinksTest {
    @Test
    fun connectionLinksRoundTripWithoutPuttingEndpointOrDisplayNameInTheToken() {
        val id = ConnectionId("3f17e9b4-stable")
        val dataSources = MarineFeatureLinks.dataSourcesForConnection(id)
        val nmeaInput = MarineFeatureLinks.nmeaInputForConnection(id)

        assertEquals(MarineFeatureDestination.DataSources(id), MarineFeatureLinks.parse(dataSources))
        assertEquals(MarineFeatureDestination.NmeaInput(id), MarineFeatureLinks.parse(nmeaInput))
        assertTrue("3f17e9b4-stable" !in dataSources.value)
        assertTrue("3f17e9b4-stable" !in nmeaInput.value)
    }

    @Test
    fun malformedOversizeAndWrongPrefixLinksAreRejected() {
        assertNull(MarineFeatureLinks.parse(MarineFeatureLinkToken("sources.connection.not-hex")))
        assertNull(MarineFeatureLinks.parse(MarineFeatureLinkToken("nmea.connection." + "aa".repeat(257))))
        assertNull(MarineFeatureLinks.parse(MarineFeatureLinkToken("chart.route.aa")))
    }
}
