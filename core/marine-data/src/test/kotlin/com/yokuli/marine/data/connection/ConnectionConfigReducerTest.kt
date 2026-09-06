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

    @Test
    fun configurationCapacityAndUserControlledTextAreBounded() {
        val boundedReducer = ConnectionConfigReducer(maxConnections = 2)
        val first = config("one", endpoint = NmeaEndpoint.TcpClient("one.local", 10_111))
        val second = config("two", endpoint = NmeaEndpoint.TcpClient("two.local", 10_112))
        val third = config("three", endpoint = NmeaEndpoint.TcpClient("three.local", 10_113))
        val withOne = boundedReducer.reduce(ConnectionConfigState(), ConnectionConfigAction.Save(first)).state
        val full = boundedReducer.reduce(withOne, ConnectionConfigAction.Save(second)).state

        val capacity = boundedReducer.reduce(full, ConnectionConfigAction.Save(third))

        assertEquals(full, capacity.state)
        assertEquals(
            setOf(ConnectionConfigIssue.CONNECTION_LIMIT_REACHED),
            (capacity.effects.single() as ConnectionConfigEffect.Invalid).issues,
        )

        val oversized = config(
            id = "oversized",
            name = "n".repeat(MAX_CONNECTION_DISPLAY_NAME_LENGTH + 1),
            endpoint = NmeaEndpoint.TcpClient(
                host = "h".repeat(MAX_CONNECTION_HOST_LENGTH + 1),
                port = 10_111,
            ),
        )
        val invalid = boundedReducer.reduce(ConnectionConfigState(), ConnectionConfigAction.Save(oversized))
        assertEquals(
            setOf(
                ConnectionConfigIssue.DISPLAY_NAME_TOO_LONG,
                ConnectionConfigIssue.TCP_HOST_TOO_LONG,
            ),
            (invalid.effects.single() as ConnectionConfigEffect.Invalid).issues,
        )
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
