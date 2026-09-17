package com.yokuli.anchorwatch.data.nmea

import org.junit.Assert.*
import org.junit.Test

class NmeaPublicationPolicyTest {
    @Test fun disabledCapabilitiesNeverPublish() {
        val sentence=NmeaChecksum.append("GPRMC,120000,A,3723.2475,N,12158.3416,W,1.2,10.0,170926,,,A")
        assertNull(NmeaPublicationPolicy.filter(sentence,emptySet()))
        assertEquals(sentence,NmeaPublicationPolicy.filter(sentence,setOf("position")))
    }
    @Test fun mixedTransducerSentenceCannotLeakDisabledPressure() {
        val sentence=NmeaChecksum.append("IIXDR,A,2.3,D,HEEL,P,1.013,B,BARO")
        val actual=NmeaPublicationPolicy.filter(sentence,setOf("attitude"))
        assertEquals(NmeaChecksum.append("IIXDR,A,2.3,D,HEEL")+"\r\n",actual)
        assertFalse(actual!!.contains("BARO"))
        assertNull(NmeaPublicationPolicy.filter(sentence,setOf("position")))
    }
    @Test fun windReferenceSelectsTheCorrectCapability() {
        assertNotNull(NmeaPublicationPolicy.filter("$"+"WIMWV,20.0,R,8.0,N,A*00",setOf("apparent_wind")))
        assertNull(NmeaPublicationPolicy.filter("$"+"WIMWV,20.0,T,8.0,N,A*00",setOf("apparent_wind")))
    }
    @Test fun emptySelectionIsNotLegacyAll() {
        val empty=NmeaConnectionSpec(capabilities=emptySet())
        assertTrue(NmeaPublicationPolicy.selected(empty).isEmpty())
        assertEquals(NmeaCapability.phone,NmeaPublicationPolicy.selected(NmeaConnectionSpec(feed=NmeaFeed.PHONE)))
    }
    @Test fun advancedSentenceFilterCannotReenableDisabledCapability() {
        val spec=NmeaConnectionSpec(sentenceTypes=setOf("HDT"),capabilities=setOf("position"))
        assertNull(NmeaPublicationPolicy.filter(spec,NmeaChecksum.append("IIHDT,123.0,T")))
    }
    @Test fun endpointNormalizationKeepsIpv6AndRemovesPorts() {
        assertEquals("192.168.1.8",NmeaPeerGuard.host("boat/192.168.1.8:10110"))
        assertEquals("fe80::1234",NmeaPeerGuard.host("/[fe80::1234%wlan0]:10110"))
        assertEquals("fe80::1234",NmeaPeerGuard.host("fe80::1234"))
    }
    @Test fun sameIpIsBlockedAcrossDifferentPortsAndLocalhostAliases() {
        val guard=NmeaPeerGuard()
        assertTrue(guard.sameHost("/127.0.0.1:10110","127.0.0.1:10111"))
        assertTrue(guard.sameHost("/127.0.0.1:10110","localhost"))
        assertFalse(guard.sameHost("/127.0.0.1:10110","192.0.2.7"))
    }
    @Test fun selectedSystemDataIsNeverLabelledAsPhoneSensors() {
        val mux=com.yokuli.anchorwatch.data.sharing.NmeaOutputMux()
        val selected=mux.selectedXdr(null,1013.0)!!
        assertTrue(selected.contains("YOKULI_BARO"))
        assertFalse(selected.contains("PHONE_"))
        assertTrue(mux.phoneXdr(null,1013.0)!!.contains("PHONE_BARO"))
    }
}
