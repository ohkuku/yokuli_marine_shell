package com.yokuli.marine.data.android

import com.yokuli.marine.data.runtime.ConnectionInputState
import com.yokuli.marine.data.runtime.ConnectionTransportState
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TcpNmeaClientIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun tcpHandshakeWithoutDataIsNotReceiving() = runBlocking {
        val release = CompletableDeferred<Unit>()
        LoopbackTcpServer { release.await() }.use { server ->
            RuntimeFixture(File(temporaryFolder.newFolder(), "connections.pb")).use { fixture ->
                val config = tcpConfig(id = "silent", port = server.port)
                fixture.start(config)
                server.accepted.await()

                val connected = fixture.await {
                    it.connections[config.id]?.transport is ConnectionTransportState.TcpConnected
                }.connections.getValue(config.id)
                delay(150L)

                assertEquals(ConnectionInputState.NO_BYTES, connected.input)
                assertEquals(0L, connected.metrics.legalFrameCount)
                release.complete(Unit)
            }
        }
        Unit
    }

    @Test
    fun windOnlyValidInputIsReceiving() = runBlocking {
        val release = CompletableDeferred<Unit>()
        LoopbackTcpServer { socket ->
            socket.getOutputStream().apply {
                write(nmea("WIMWV,045.0,R,10.5,N,A").toByteArray(Charsets.US_ASCII))
                flush()
            }
            release.await()
        }.use { server ->
            RuntimeFixture(File(temporaryFolder.newFolder(), "connections.pb")).use { fixture ->
                val config = tcpConfig(id = "wind-only", port = server.port)
                fixture.start(config)

                val receiving = fixture.await {
                    it.connections[config.id]?.input == ConnectionInputState.RECEIVING_VALID_FRAMES
                }.connections.getValue(config.id)

                assertTrue(receiving.transport is ConnectionTransportState.TcpConnected)
                assertEquals(1L, receiving.metrics.legalFrameCount)
                assertEquals(0L, receiving.metrics.positionFrameCount)
                release.complete(Unit)
            }
        }
        Unit
    }

    @Test
    fun badChecksumInputNeverBecomesReceiving() = runBlocking {
        val release = CompletableDeferred<Unit>()
        LoopbackTcpServer { socket ->
            socket.getOutputStream().apply {
                write(badChecksum("GPRMC,120000,A,3650.9100,S,17445.8000,E,4.5,84.4,260826,12.0,E,A")
                    .toByteArray(Charsets.US_ASCII))
                flush()
            }
            release.await()
        }.use { server ->
            RuntimeFixture(File(temporaryFolder.newFolder(), "connections.pb")).use { fixture ->
                val config = tcpConfig(id = "bad-checksum", port = server.port)
                fixture.start(config)

                val invalid = fixture.await {
                    it.connections[config.id]?.metrics?.checksumFailureCount == 1L
                }.connections.getValue(config.id)

                assertTrue(invalid.transport is ConnectionTransportState.TcpConnected)
                assertEquals(ConnectionInputState.BYTES_WITHOUT_VALID_FRAME, invalid.input)
                assertEquals(0L, invalid.metrics.legalFrameCount)
                release.complete(Unit)
            }
        }
        Unit
    }

    @Test
    fun oversizedTcpFrameIsCountedAndAllIngressBuffersRemainBounded() = runBlocking {
        val release = CompletableDeferred<Unit>()
        LoopbackTcpServer { socket ->
            socket.getOutputStream().apply {
                write(("$" + "X".repeat(8_192) + "\r\n").toByteArray(Charsets.US_ASCII))
                flush()
            }
            release.await()
        }.use { server ->
            RuntimeFixture(File(temporaryFolder.newFolder(), "connections.pb")).use { fixture ->
                val config = tcpConfig(id = "bounded-frame", port = server.port)
                fixture.start(config)

                val bounded = fixture.await {
                    it.connections[config.id]?.metrics?.frameOverflowCount == 1L
                }.connections.getValue(config.id)

                assertTrue(bounded.metrics.tcpBufferedByteCount <= 1_024)
                assertTrue(bounded.metrics.pendingIngressFrameCount <= 64)
                assertTrue(fixture.runtime.state.value.incidents.size <= 128)
                assertEquals(0L, bounded.metrics.legalFrameCount)
                release.complete(Unit)
            }
        }
        Unit
    }
}
