package com.yokuli.marine.feature.nmeainput

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.nmea.ChecksumPolicy
import org.junit.Assert.assertNull
import org.junit.Test

class NmeaInputReducerTest {
    @Test
    fun udpRestrictionCannotBeBypassedByCallingDraftConversionDirectly() {
        val draft = NmeaConnectionDraft(
            id = ConnectionId("restricted-udp"),
            name = "Restricted listener",
            transport = NmeaInputTransportKind.UDP_LISTENER,
            tcpHost = "",
            portText = "10112",
            restrictUdpSender = true,
            udpSenderAddress = " ",
            checksumPolicy = ChecksumPolicy.STRICT,
        )

        assertNull(draft.configOrNull())
    }

    @Test
    fun fullWidthDigitsAreNotAcceptedAsAPort() {
        val draft = NmeaConnectionDraft(
            id = ConnectionId("unicode-port"),
            name = "Listener",
            transport = NmeaInputTransportKind.UDP_LISTENER,
            tcpHost = "",
            portText = "１０１１２",
            restrictUdpSender = false,
            udpSenderAddress = "",
            checksumPolicy = ChecksumPolicy.STRICT,
        )

        assertNull(draft.configOrNull())
    }
}
