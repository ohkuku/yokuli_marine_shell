package com.yokuli.marine.data.android

import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionTransportState
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UdpNmeaListenerIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun udpBindWithoutDatagramIsListeningNotConnected() = runBlocking {
        val port = reserveUdpPort()
        RuntimeFixture(File(temporaryFolder.newFolder(), "connections.pb")).use { fixture ->
            val config = udpConfig(id = "silent-udp", port = port)
            fixture.start(config)

            val listening = fixture.await {
                it.connections[config.id]?.transport is ConnectionTransportState.UdpListening
            }.connections.getValue(config.id)
            delay(150L)

            assertEquals(ConnectionInputState.NO_BYTES, listening.input)
            assertEquals(0L, listening.metrics.datagramCount)
            assertTrue(listening.metrics.observedUdpSenders.isEmpty())
        }
    }

    @Test
    fun udpDatagramsNeverJoinFragmentsAcrossSendersOrPacketBoundaries() = runBlocking {
        val port = reserveUdpPort()
        RuntimeFixture(File(temporaryFolder.newFolder(), "connections.pb")).use { fixture ->
            val config = udpConfig(id = "isolated-datagrams", port = port)
            fixture.start(config)
            fixture.await { it.connections[config.id]?.transport is ConnectionTransportState.UdpListening }

            DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { first ->
                // Distinct ephemeral ports are distinct observed senders while remaining portable
                // to hosts that do not bind every address in the 127/8 loopback range.
                DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { second ->
                    val complete = nmea("WIMWV,045.0,R,10.5,N,A").toByteArray(Charsets.US_ASCII)
                    val splitAt = complete.size / 2
                    first.sendTo(complete.copyOfRange(0, splitAt), port)
                    second.sendTo(complete.copyOfRange(splitAt, complete.size), port)

                    val fragments = fixture.await {
                        it.connections[config.id]?.metrics?.datagramCount == 2L
                    }.connections.getValue(config.id)
                    assertEquals(0L, fragments.metrics.legalFrameCount)

                    first.sendTo(complete, port)
                    second.sendTo(complete, port)

                    val valid = fixture.await {
                        it.connections[config.id]?.metrics?.legalFrameCount == 2L
                    }.connections.getValue(config.id)
                    assertEquals(ConnectionInputState.RECEIVING_VALID_FRAMES, valid.input)
                    assertEquals(2, valid.metrics.observedUdpSenders.size)
                    assertEquals(
                        setOf(first.localPort, second.localPort),
                        valid.metrics.observedUdpSenders.map { it.port }.toSet(),
                    )
                }
            }
        }
    }

    @Test
    fun oversizedUdpDatagramIsRejectedWithoutGrowingRetainedState() = runBlocking {
        val port = reserveUdpPort()
        RuntimeFixture(File(temporaryFolder.newFolder(), "connections.pb")).use { fixture ->
            val config = udpConfig(id = "bounded-udp", port = port)
            fixture.start(config)
            fixture.await { it.connections[config.id]?.transport is ConnectionTransportState.UdpListening }

            DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { sender ->
                sender.sendTo(ByteArray(16_384) { 'X'.code.toByte() }, port)
            }

            val bounded = fixture.await {
                it.connections[config.id]?.metrics?.datagramOverflowCount == 1L
            }.connections.getValue(config.id)
            assertEquals(0L, bounded.metrics.legalFrameCount)
            assertTrue(bounded.metrics.pendingIngressFrameCount <= 64)
            assertTrue(fixture.runtime.state.value.incidents.size <= 128)
        }
    }

    private fun DatagramSocket.sendTo(bytes: ByteArray, port: Int) {
        send(DatagramPacket(bytes, bytes.size, InetAddress.getByName("127.0.0.1"), port))
    }

    private fun reserveUdpPort(): Int = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use {
        it.localPort
    }
}
