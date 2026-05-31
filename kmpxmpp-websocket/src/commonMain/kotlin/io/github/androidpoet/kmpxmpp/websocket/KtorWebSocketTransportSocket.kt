package io.github.androidpoet.kmpxmpp.websocket

import io.github.androidpoet.kmpxmpp.transport.TransportSocket
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.withTimeout

internal expect fun createPlatformWebSocketHttpClient(readTimeoutMillis: Long): HttpClient

public class KtorWebSocketTransportSocket(
    private val path: String = "/xmpp-websocket",
    private val secure: Boolean = false,
    private val readTimeoutMillis: Long = 30_000,
) : TransportSocket {
    private var client: HttpClient? = null
    private var session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession? = null

    override suspend fun connect(host: String, port: Int) {
        close()

        val scheme = if (secure) "wss" else "ws"
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val endpoint = "$scheme://$host:$port$normalizedPath"

        val httpClient = createPlatformWebSocketHttpClient(readTimeoutMillis)
        client = httpClient
        session = httpClient.webSocketSession(endpoint)
    }

    override suspend fun write(data: String) {
        val activeSession = session ?: error("WebSocket is not connected.")
        activeSession.send(data)
    }

    override suspend fun read(): String {
        val activeSession = session ?: error("WebSocket is not connected.")
        while (true) {
            val frame = withTimeout(readTimeoutMillis) {
                activeSession.incoming.receive()
            }

            when (frame) {
                is Frame.Text -> return frame.readText()
                is Frame.Binary -> return frame.data.decodeToString()
                is Frame.Close -> throw IllegalStateException(
                    "WebSocket closed: ${frame.readReason()?.message ?: "unknown"}",
                )
                else -> Unit
            }
        }
    }

    override suspend fun close() {
        session?.close()
        session = null
        client?.close()
        client = null
    }
}
