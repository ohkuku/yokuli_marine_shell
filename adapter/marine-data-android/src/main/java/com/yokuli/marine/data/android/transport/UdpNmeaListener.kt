package com.yokuli.marine.data.android.transport

import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.model.SenderIdentity
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.TransportFailureKind
import java.io.IOException
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

class UdpNmeaListener(
    private val receiveBufferBytes: Int = MAX_UDP_DATAGRAM_BYTES,
) {
    init {
        require(receiveBufferBytes in 1..MAX_UDP_DATAGRAM_BYTES)
    }

    suspend fun run(
        endpoint: NmeaEndpoint.UdpListener,
        emit: suspend (NetworkTransportEvent) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val socket = DatagramSocket(null)
        val closeOnCompletion = coroutineContext.job.invokeOnCompletion { socket.close() }
        try {
            socket.reuseAddress = false
            socket.bind(InetSocketAddress(endpoint.localPort))
            val acceptedAddresses = endpoint.senderHostFilter?.let(::resolveAddresses)
            emit(NetworkTransportEvent.UdpListening(endpoint.localPort))

            val receiveBuffer = ByteArray(receiveBufferBytes)
            val packet = DatagramPacket(receiveBuffer, receiveBuffer.size)
            while (true) {
                coroutineContext.ensureActive()
                packet.length = receiveBuffer.size
                socket.receive(packet)
                val senderHost = requireNotNull(packet.address.hostAddress)
                if (acceptedAddresses == null || senderHost in acceptedAddresses) {
                    emit(
                        NetworkTransportEvent.UdpDatagram(
                            bytes = packet.data.copyOfRange(packet.offset, packet.offset + packet.length),
                            sender = SenderIdentity(
                                hostAddress = senderHost,
                                port = packet.port,
                            ),
                        ),
                    )
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            coroutineContext.ensureActive()
            val failure = if (error is BindException) {
                NmeaRuntimeFailure.UdpAddressInUse(endpoint.localPort)
            } else {
                NmeaRuntimeFailure.TransportFailed(error.failureKind())
            }
            emit(
                NetworkTransportEvent.Failed(
                    failure = failure,
                    recoverable = error is IOException && error !is BindException && error !is UnknownHostException,
                ),
            )
        } finally {
            closeOnCompletion.dispose()
            socket.close()
        }
    }

    private fun resolveAddresses(host: String): Set<String> =
        InetAddress.getAllByName(host).mapNotNull(InetAddress::getHostAddress).toSet()

    private fun Throwable.failureKind(): TransportFailureKind = when (this) {
        is UnknownHostException -> TransportFailureKind.DNS
        is SocketException, is IOException -> TransportFailureKind.IO
        else -> TransportFailureKind.UNKNOWN
    }

    companion object {
        /** Maximum legal UDP payload. Runtime framing applies the tighter 1024-byte product bound. */
        const val MAX_UDP_DATAGRAM_BYTES = 65_507
    }
}
