package com.yokuli.anchorwatch.data.nmea

import java.net.DatagramPacket
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class NmeaReviewRegressionTest {
    @Test fun sameBootRestoresOnlyExplicitRemainingLeases() {
        val started=setOf("boat-gps","wind")
        val afterStop=started-"boat-gps"
        assertEquals(setOf("wind"),NmeaConnectionLeasePolicy.restorable(19,19,afterStop))
        assertTrue(NmeaConnectionLeasePolicy.restorable(19,20,started).isEmpty())
        assertTrue(NmeaConnectionLeasePolicy.restorable(-1,-1,started).isEmpty())
    }
    @Test fun packetIpv6PeerUsesTransportFormatterAndBlocksAnotherPort() {
        val packet=DatagramPacket(ByteArray(1),1,InetAddress.getByName("0000:0000:0000:0000:0000:0000:0000:0001"),19171)
        val peer=NmeaPeerGuard.endpoint(packet.address,packet.port)
        assertTrue(peer.startsWith("["))
        assertTrue(NmeaPeerGuard().sameHost(peer,"[::1]:19172"))
        assertFalse(NmeaPeerGuard().sameHost(peer,"[2001:db8::4]:19172"))
    }
    @Test fun aisFramingSurvivesChunksChecksumAndOtherFilter() {
        val sentence="!AIVDM,1,1,,A,15Muq?P0000G?t@E>4@>4?wP0<0u,0*1C"
        val splitter=NmeaStreamSplitter()
        assertTrue(splitter.feed(sentence.take(11)).isEmpty())
        val frames=splitter.feed(sentence.drop(11)+"\r\n")
        assertEquals(listOf(sentence),frames)
        assertTrue(NmeaChecksum.validate(frames.single()))
        assertEquals(sentence,NmeaPublicationPolicy.filter(frames.single(),setOf("other")))
        assertNull(NmeaPublicationPolicy.filter(frames.single(),emptySet()))
        assertFalse(NmeaChecksum.validate(sentence.dropLast(2)+"00"))
    }
    @Test fun vhwCanShareWaterSpeedWithoutHeadingOrViceVersa() {
        val sentence=NmeaChecksum.append("IIVHW,123.0,T,110.0,M,5.0,N,9.26,K")
        val speed=NmeaPublicationPolicy.filter(sentence,setOf("water_speed"))!!
        val heading=NmeaPublicationPolicy.filter(sentence,setOf("heading"))!!
        assertTrue(NmeaChecksum.validate(speed));assertTrue(NmeaChecksum.validate(heading))
        assertFalse(speed.contains("123.0"));assertFalse(speed.contains("110.0"));assertTrue(speed.contains("5.0,N"))
        assertTrue(heading.contains("123.0,T"));assertFalse(heading.contains("5.0,N"))
        assertNull(NmeaPublicationPolicy.filter(sentence,setOf("other")))
    }
    @Test fun mdaOtherCannotLeakPressureTemperatureOrWind() {
        val sentence=NmeaChecksum.append("IIMDA,29.91,I,1.013,B,20.0,C,17.0,C,60.0,,10.0,C,180.0,T,170.0,M,12.0,N,6.17,M")
        val other=NmeaPublicationPolicy.filter(sentence,setOf("other"))!!
        assertTrue(NmeaChecksum.validate(other))
        val fields=other.substringBefore('*').split(',')
        assertEquals("60.0",fields[9])
        listOf(1,3,5,7,11,13,15,17,19).forEach{assertEquals("",fields[it])}
        val wind=NmeaPublicationPolicy.filter(sentence,setOf("true_wind"))!!
        assertTrue(wind.contains("180.0,T"));assertFalse(wind.contains("1.013"));assertFalse(wind.contains("60.0"))
    }
}
