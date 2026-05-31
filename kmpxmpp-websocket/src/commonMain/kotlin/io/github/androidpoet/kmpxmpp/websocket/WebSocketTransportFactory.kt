package io.github.androidpoet.kmpxmpp.websocket

import io.github.androidpoet.kmpxmpp.transport.DefaultXmppTransport
import io.github.androidpoet.kmpxmpp.transport.XmppTransport

public fun createWebSocketXmppTransport(
    path: String = "/xmpp-websocket",
    secure: Boolean = false,
    readTimeoutMillis: Long = 30_000,
): XmppTransport =
    DefaultXmppTransport(
        socket = KtorWebSocketTransportSocket(
            path = path,
            secure = secure,
            readTimeoutMillis = readTimeoutMillis,
        ),
    )
