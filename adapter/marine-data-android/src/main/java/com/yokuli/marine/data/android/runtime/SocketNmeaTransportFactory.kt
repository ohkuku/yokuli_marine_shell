package com.yokuli.marine.data.android.runtime

import com.yokuli.marine.data.android.transport.NetworkTransportEvent
import com.yokuli.marine.data.android.transport.NmeaTransportFactory
import com.yokuli.marine.data.android.transport.TcpNmeaClient
import com.yokuli.marine.data.android.transport.UdpNmeaListener
import com.yokuli.marine.data.connection.NmeaConnectionConfig
import com.yokuli.marine.data.connection.NmeaEndpoint
import com.yokuli.marine.data.runtime.SessionToken

class SocketNmeaTransportFactory(
    private val tcpClient: TcpNmeaClient = TcpNmeaClient(),
    private val udpListener: UdpNmeaListener = UdpNmeaListener(),
) : NmeaTransportFactory {
    override suspend fun run(
        config: NmeaConnectionConfig,
        sessionToken: SessionToken,
        emit: suspend (NetworkTransportEvent) -> Unit,
    ) {
        when (val endpoint = config.endpoint) {
            is NmeaEndpoint.TcpClient -> tcpClient.run(endpoint, emit)
            is NmeaEndpoint.UdpListener -> udpListener.run(endpoint, emit)
        }
    }
}
