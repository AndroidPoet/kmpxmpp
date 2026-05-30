package io.github.androidpoet.kmpxmpp.websocket

import io.github.androidpoet.kmpxmpp.transport.DefaultXmppTransport
import io.github.androidpoet.kmpxmpp.transport.TransportSocket
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

public class JvmWebSocketTransportSocket(
    private val path: String = "/xmpp-websocket",
    private val secure: Boolean = false,
    private val readTimeoutMillis: Long = 30_000,
) : TransportSocket {
    private val inboundQueue: LinkedBlockingQueue<String> = LinkedBlockingQueue()
    private var webSocket: WebSocket? = null

    override suspend fun connect(host: String, port: Int) {
        val scheme = if (secure) "wss" else "ws"
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val uri = URI.create("$scheme://$host:$port$normalizedPath")
        val listener = QueueingListener(inboundQueue)

        webSocket = HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .buildAsync(uri, listener)
            .join()
    }

    override suspend fun write(data: String) {
        val socket = webSocket ?: error("WebSocket is not connected.")
        socket.sendText(data, true).join()
    }

    override suspend fun read(): String {
        val socket = webSocket ?: error("WebSocket is not connected.")
        val payload = inboundQueue.poll(readTimeoutMillis, TimeUnit.MILLISECONDS)
        if (payload != null) {
            return payload
        }

        if (socket.isOutputClosed || socket.isInputClosed) {
            throw IllegalStateException("WebSocket is closed.")
        }

        throw IllegalStateException("Timed out waiting for WebSocket message.")
    }

    override suspend fun close() {
        val socket = webSocket
        webSocket = null
        inboundQueue.clear()
        socket?.sendClose(WebSocket.NORMAL_CLOSURE, "bye")?.join()
    }
}

private class QueueingListener(
    private val inboundQueue: LinkedBlockingQueue<String>,
) : WebSocket.Listener {
    private val partial = StringBuilder()

    override fun onOpen(webSocket: WebSocket) {
        webSocket.request(1)
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletableFuture<*> {
        partial.append(data)
        if (last) {
            inboundQueue.offer(partial.toString())
            partial.setLength(0)
        }
        webSocket.request(1)
        return CompletableFuture.completedFuture(null)
    }

    override fun onError(webSocket: WebSocket, error: Throwable) {
        inboundQueue.offer("ERROR:${error.message ?: "unknown"}")
    }
}

public fun createJvmWebSocketXmppTransport(
    path: String = "/xmpp-websocket",
    secure: Boolean = false,
    readTimeoutMillis: Long = 30_000,
): XmppTransport =
    DefaultXmppTransport(
        socket = JvmWebSocketTransportSocket(
            path = path,
            secure = secure,
            readTimeoutMillis = readTimeoutMillis,
        ),
    )
