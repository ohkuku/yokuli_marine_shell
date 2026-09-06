package com.yokuli.marine.data.android.transport

import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.runtime.NmeaRuntimeFailure
import com.yokuli.marine.data.runtime.TransportFailureKind
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

class TcpNmeaClient(
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readBufferBytes: Int = DEFAULT_READ_BUFFER_BYTES,
) {
    init {
        require(connectTimeoutMillis > 0)
        require(readBufferBytes in 1..MAX_READ_BUFFER_BYTES)
    }

    suspend fun run(
        endpoint: NmeaEndpoint.TcpClient,
        emit: suspend (NetworkTransportEvent) -> Unit,
    ): Unit = withContext(Dispatchers.IO) {
        val socket = Socket()
        val closeOnCompletion = coroutineContext.job.invokeOnCompletion { socket.closeQuietly() }
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.connect(InetSocketAddress(endpoint.host, endpoint.port), connectTimeoutMillis)
            emit(NetworkTransportEvent.TcpConnected)

            val buffer = ByteArray(readBufferBytes)
            val input = socket.getInputStream()
            while (true) {
                coroutineContext.ensureActive()
                val count = input.read(buffer)
                if (count < 0) {
                    emit(
                        NetworkTransportEvent.Failed(
                            NmeaRuntimeFailure.TransportFailed(TransportFailureKind.IO),
                            recoverable = true,
                        ),
                    )
                    break
                }
                if (count > 0) {
                    emit(NetworkTransportEvent.TcpBytes(buffer.copyOf(count)))
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            coroutineContext.ensureActive()
            emit(
                NetworkTransportEvent.Failed(
                    failure = NmeaRuntimeFailure.TransportFailed(error.failureKind()),
                    recoverable = error is IOException,
                ),
            )
        } finally {
            closeOnCompletion.dispose()
            socket.closeQuietly()
        }
    }

    private fun Throwable.failureKind(): TransportFailureKind = when (this) {
        is UnknownHostException -> TransportFailureKind.DNS
        is SocketTimeoutException -> TransportFailureKind.TIMEOUT
        is ConnectException -> TransportFailureKind.CONNECTION_REFUSED
        is IOException -> TransportFailureKind.IO
        else -> TransportFailureKind.UNKNOWN
    }

    private fun Socket.closeQuietly() {
        runCatching(::close)
    }

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000
        const val DEFAULT_READ_BUFFER_BYTES = 4_096
        const val MAX_READ_BUFFER_BYTES = 64 * 1_024
    }
}
