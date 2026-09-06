package com.yokuli.marine.data.nmea

import com.yokuli.marine.data.model.SenderIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class NmeaFramerTest {
    @Test
    fun tcpFramingHandlesHalfPacketsGlueAndDisconnectFragments() {
        val framer = TcpNmeaFramer()

        assertTrue(framer.accept("\$GPR".toByteArray()).isEmpty())
        assertEquals(
            listOf(
                NmeaFrameEvent.Frame("\$GPRMC,one*00"),
                NmeaFrameEvent.Frame("\$GPGGA,two*00"),
            ),
            framer.accept("MC,one*00\r\n\$GPGGA,two*00\n".toByteArray()),
        )

        framer.accept("\$GPGLL,incomplete".toByteArray())
        assertEquals(
            listOf(NmeaFrameEvent.FragmentDiscarded("\$GPGLL,incomplete".length)),
            framer.resetOnDisconnect(),
        )
        assertTrue(framer.accept("tail\r\n".toByteArray()).single() is NmeaFrameEvent.Frame)
    }

    @Test
    fun tcpFramerReportsOverflowAndInvalidEncodingWithoutGrowingForever() {
        val framer = TcpNmeaFramer(maxFrameBytes = 12)

        val overflow = framer.accept(("$" + "X".repeat(20) + "\r\n").toByteArray())
        assertEquals(1, overflow.size)
        assertTrue(overflow.single() is NmeaFrameEvent.Overflow)
        assertEquals(0, framer.bufferedByteCount)

        val invalid = framer.accept(byteArrayOf('$'.code.toByte(), 0x80.toByte(), '\n'.code.toByte()))
        assertTrue(invalid.single() is NmeaFrameEvent.InvalidEncoding)
        assertEquals(0, framer.bufferedByteCount)
    }

    @Test
    fun tcpFramingIsIndependentOfNetworkChunkBoundaries() {
        val expected = (0 until 60).map { index -> "\$PYOK,$index,${"X".repeat(index % 19)}*00" }
        val wire = expected.joinToString(separator = "") { "$it\r\n" }.toByteArray()

        repeat(100) { seed ->
            val random = Random(seed.toLong())
            val framer = TcpNmeaFramer()
            val actual = mutableListOf<String>()
            var offset = 0
            while (offset < wire.size) {
                val count = minOf(wire.size - offset, 1 + random.nextInt(31))
                actual += framer.accept(wire.copyOfRange(offset, offset + count))
                    .filterIsInstance<NmeaFrameEvent.Frame>()
                    .map(NmeaFrameEvent.Frame::raw)
                offset += count
            }
            assertEquals("fragmentation seed $seed", expected, actual)
            assertEquals(0, framer.bufferedByteCount)
        }
    }

    @Test
    fun udpNeverCombinesFragmentsAcrossDatagramsOrSenders() {
        val framer = UdpNmeaDatagramFramer()
        val senderA = SenderIdentity("192.0.2.10", 20_001)
        val senderB = SenderIdentity("192.0.2.11", 20_002)

        val first = framer.frame("\$GPR".toByteArray(), senderA)
        val second = framer.frame("MC,tail*00\n\$IIDPT,4.2,0*00".toByteArray(), senderB)

        assertEquals(senderA, first.sender)
        assertEquals(listOf(NmeaFrameEvent.Frame("\$GPR")), first.events)
        assertEquals(senderB, second.sender)
        assertEquals(
            listOf(
                NmeaFrameEvent.Frame("MC,tail*00"),
                NmeaFrameEvent.Frame("\$IIDPT,4.2,0*00"),
            ),
            second.events,
        )
    }
}
