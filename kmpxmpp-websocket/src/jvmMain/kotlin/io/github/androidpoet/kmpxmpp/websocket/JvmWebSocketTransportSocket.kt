package io.github.androidpoet.kmpxmpp.websocket

import io.github.androidpoet.kmpxmpp.transport.TransportSocket
import io.github.androidpoet.kmpxmpp.transport.XmppTransport

public class JvmWebSocketTransportSocket(
    private val path: String = "/xmpp-websocket",
    private val secure: Boolean = false,
    private val readTimeoutMillis: Long = 30_000,
) : TransportSocket {
    private val delegate: KtorWebSocketTransportSocket =
        KtorWebSocketTransportSocket(
            path = path,
            secure = secure,
            readTimeoutMillis = readTimeoutMillis,
        )

    override suspend fun connect(host: String, port: Int) {
        delegate.connect(host = host, port = port)
    }

    override suspend fun write(data: String) {
        delegate.write(data)
    }

    override suspend fun read(): String {
        return delegate.read()
    }

    override suspend fun close() {
        delegate.close()
    }
}

@Deprecated(
    message = "Use createWebSocketXmppTransport(...) for platform-neutral API.",
    replaceWith = ReplaceWith("createWebSocketXmppTransport(path, secure, readTimeoutMillis)"),
)
public fun createJvmWebSocketXmppTransport(
    path: String = "/xmpp-websocket",
    secure: Boolean = false,
    readTimeoutMillis: Long = 30_000,
): XmppTransport =
    createWebSocketXmppTransport(
        path = path,
        secure = secure,
        readTimeoutMillis = readTimeoutMillis,
    )
