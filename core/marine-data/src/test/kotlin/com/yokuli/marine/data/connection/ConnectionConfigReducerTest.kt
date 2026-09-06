package com.yokuli.marine.data.connection

import com.yokuli.marine.data.model.ConnectionId
import com.yokuli.marine.data.nmea.ChecksumPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionConfigReducerTest {
    private val reducer = ConnectionConfigReducer()

    @Test
    fun invalidConfigurationIsRejectedWithoutMutatingOrPersistingState() {
        val initial = ConnectionConfigState()
        val invalidTcp = config(
            id = "invalid-tcp",
            name = " ",
            endpoint = NmeaEndpoint.TcpClient(host = " ", port = 0),
        )

        val tcpResult = reducer.reduce(initial, ConnectionConfigAction.Save(invalidTcp))

        assertEquals(initial, tcpResult.state)
        assertEquals(
            setOf(
                ConnectionConfigIssue.BLANK_DISPLAY_NAME,
                ConnectionConfigIssue.BLANK_TCP_HOST,
                ConnectionConfigIssue.PORT_OUT_OF_RANGE,
            ),
            (tcpResult.effects.single() as ConnectionConfigEffect.Invalid).issues,
        )
        assertTrue(tcpResult.effects.none { it is ConnectionConfigEffect.Persist })

        val invalidUdp = config(
            id = "invalid-udp",
            endpoint = NmeaEndpoint.UdpListener(localPort = 65_536),
        )
        val udpResult = reducer.reduce(initial, ConnectionConfigAction.Save(invalidUdp))
        assertEquals(initial, udpResult.state)
        assertEquals(
            setOf(ConnectionConfigIssue.PORT_OUT_OF_RANGE),
            (udpResult.effects.single() as ConnectionConfigEffect.Invalid).issues,
        )
    }

    @Test
    fun duplicateTcpEndpointRequiresExplicitConfirmationBeforeSecondConfigIsSaved() {
        val first = config(
            id = "primary",
            endpoint = NmeaEndpoint.TcpClient("boat-gateway.local", 10_111),
        )
        val second = config(
            id = "backup",
            endpoint = NmeaEndpoint.TcpClient(" BOAT-GATEWAY.LOCAL. ", 10_111),
        )
        val withFirst = reducer.reduce(ConnectionConfigState(), ConnectionConfigAction.Save(first))
        assertEquals(mapOf(first.id to first), withFirst.state.connections)
        assertTrue(withFirst.effects.single() is ConnectionConfigEffect.Persist)

        val rejected = reducer.reduce(withFirst.state, ConnectionConfigAction.Save(second))

        assertEquals(withFirst.state, rejected.state)
        val confirmation = rejected.effects.single() as ConnectionConfigEffect.ConfirmDuplicateTcp
        assertEquals(second, confirmation.candidate)
        assertEquals(setOf(first.id), confirmation.existingConnectionIds)

        val confirmed = reducer.reduce(
            rejected.state,
            ConnectionConfigAction.Save(second, duplicateTcpConfirmed = true),
        )
        assertEquals(setOf(first.id, second.id), confirmed.state.connections.keys)
        assertTrue(confirmed.effects.single() is ConnectionConfigEffect.Persist)
    }

    @Test
    fun aUdpListenerIsNotComparedAsThoughItWereARemoteTcpEndpoint() {
        val tcp = config(
            id = "tcp",
            endpoint = NmeaEndpoint.TcpClient("127.0.0.1", 10_112),
        )
        val udp = config(
            id = "udp",
            endpoint = NmeaEndpoint.UdpListener(localPort = 10_112),
        )
        val withTcp = reducer.reduce(ConnectionConfigState(), ConnectionConfigAction.Save(tcp)).state

        val result = reducer.reduce(withTcp, ConnectionConfigAction.Save(udp))

        assertEquals(setOf(tcp.id, udp.id), result.state.connections.keys)
        assertTrue(result.effects.single() is ConnectionConfigEffect.Persist)
    }

    private fun config(
        id: String,
        name: String = "Boat gateway",
        endpoint: NmeaEndpoint,
    ) = NmeaConnectionConfig(
        id = ConnectionId(id),
        displayName = name,
        endpoint = endpoint,
        checksumPolicy = ChecksumPolicy.STRICT,
    )
}
