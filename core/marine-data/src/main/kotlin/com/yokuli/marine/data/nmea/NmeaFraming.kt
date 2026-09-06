package com.yokuli.marine.data.nmea

import com.yokuli.marine.data.model.SenderIdentity

const val MAX_FRAME_BYTES: Int = 1_024

sealed interface NmeaFrameEvent {
    data class Frame(val raw: String) : NmeaFrameEvent
    data class Overflow(val discardedByteCount: Int) : NmeaFrameEvent
    data class InvalidEncoding(val discardedByteCount: Int) : NmeaFrameEvent
    data class FragmentDiscarded(val discardedByteCount: Int) : NmeaFrameEvent
}

/**
 * Stateful TCP line framer. It deliberately does not validate NMEA syntax; every bounded ASCII
 * line reaches the parser/diagnostics layer. A connection-generation boundary must call
 * [resetOnDisconnect], so a tail from one socket can never join bytes from its replacement.
 */
class TcpNmeaFramer(
    private val maxFrameBytes: Int = MAX_FRAME_BYTES,
) {
    init {
        require(maxFrameBytes > 0)
    }

    private val buffer = ArrayList<Byte>(minOf(maxFrameBytes, 128))
    private var discardReason: DiscardReason? = null
    private var discardedByteCount: Int = 0

    val bufferedByteCount: Int
        get() = buffer.size

    fun accept(bytes: ByteArray, count: Int = bytes.size): List<NmeaFrameEvent> {
        require(count in 0..bytes.size)
        val events = mutableListOf<NmeaFrameEvent>()
        for (index in 0 until count) {
            val byte = bytes[index]
            val unsigned = byte.toInt() and 0xff

            discardReason?.let { reason ->
                discardedByteCount += 1
                if (unsigned == LINE_FEED) {
                    events += reason.toEvent(discardedByteCount)
                    discardReason = null
                    discardedByteCount = 0
                }
                return@let
            } ?: run {
                when {
                    unsigned == LINE_FEED -> emitBufferedLine(events)
                    unsigned == CARRIAGE_RETURN || unsigned in ASCII_PRINTABLE_RANGE -> {
                        if (buffer.size == maxFrameBytes) {
                            beginDiscard(DiscardReason.OVERFLOW, buffer.size + 1)
                        } else {
                            buffer += byte
                        }
                    }
                    else -> beginDiscard(DiscardReason.INVALID_ENCODING, buffer.size + 1)
                }
            }
        }
        return events
    }

    fun resetOnDisconnect(): List<NmeaFrameEvent> {
        val event = when (val reason = discardReason) {
            null -> buffer.takeIf { it.isNotEmpty() }?.let {
                NmeaFrameEvent.FragmentDiscarded(it.size)
            }
            else -> reason.toEvent(discardedByteCount)
        }
        buffer.clear()
        discardReason = null
        discardedByteCount = 0
        return listOfNotNull(event)
    }

    private fun beginDiscard(reason: DiscardReason, count: Int) {
        buffer.clear()
        discardReason = reason
        discardedByteCount = count
    }

    private fun emitBufferedLine(events: MutableList<NmeaFrameEvent>) {
        if (buffer.lastOrNull()?.toInt() == CARRIAGE_RETURN) {
            buffer.removeAt(buffer.lastIndex)
        }
        if (buffer.isNotEmpty()) {
            events += NmeaFrameEvent.Frame(buffer.toAsciiString())
        }
        buffer.clear()
    }

    private enum class DiscardReason {
        OVERFLOW,
        INVALID_ENCODING;

        fun toEvent(byteCount: Int): NmeaFrameEvent = when (this) {
            OVERFLOW -> NmeaFrameEvent.Overflow(byteCount)
            INVALID_ENCODING -> NmeaFrameEvent.InvalidEncoding(byteCount)
        }
    }

    private fun List<Byte>.toAsciiString(): String = buildString(size) {
        this@toAsciiString.forEach { append((it.toInt() and 0xff).toChar()) }
    }

    private companion object {
        const val LINE_FEED = 0x0a
        const val CARRIAGE_RETURN = 0x0d
        val ASCII_PRINTABLE_RANGE = 0x20..0x7e
    }
}

data class UdpFrameBatch(
    val sender: SenderIdentity,
    val events: List<NmeaFrameEvent>,
)

/** Each datagram is framed in isolation. No tail is retained across datagrams or senders. */
class UdpNmeaDatagramFramer(
    private val maxFrameBytes: Int = MAX_FRAME_BYTES,
) {
    init {
        require(maxFrameBytes > 0)
    }

    fun frame(datagram: ByteArray, sender: SenderIdentity): UdpFrameBatch {
        if (datagram.isEmpty()) return UdpFrameBatch(sender, emptyList())
        val terminated = if (datagram.last().toInt() and 0xff == 0x0a) {
            datagram
        } else {
            datagram.copyOf(datagram.size + 1).also { it[it.lastIndex] = 0x0a }
        }
        val events = TcpNmeaFramer(maxFrameBytes).accept(terminated)
        return UdpFrameBatch(sender = sender, events = events)
    }
}
