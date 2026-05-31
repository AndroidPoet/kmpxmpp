package io.github.androidpoet.kmpxmpp.tcp

import io.github.androidpoet.kmpxmpp.transport.DefaultXmppTransport
import io.github.androidpoet.kmpxmpp.transport.TransportSocket
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.SocketTimeoutException
import java.net.Socket

public class JvmTcpTransportSocket : TransportSocket {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    override suspend fun connect(host: String, port: Int) {
        val connected = Socket(host, port)
        connected.soTimeout = DEFAULT_READ_TIMEOUT_MILLIS
        socket = connected
        reader = BufferedReader(InputStreamReader(connected.getInputStream(), Charsets.UTF_8))
        writer = BufferedWriter(OutputStreamWriter(connected.getOutputStream(), Charsets.UTF_8))
    }

    override suspend fun write(data: String) {
        val currentWriter = writer ?: error("TCP socket is not connected.")
        currentWriter.write(data)
        currentWriter.flush()
    }

    override suspend fun read(): String {
        val currentReader = reader ?: error("TCP socket is not connected.")
        val builder = StringBuilder()
        val buffer = CharArray(1024)

        while (true) {
            val readCount = try {
                currentReader.read(buffer, 0, buffer.size)
            } catch (_: SocketTimeoutException) {
                if (builder.isNotEmpty()) {
                    break
                } else {
                    throw IllegalStateException("Timed out waiting for TCP data.")
                }
            }

            if (readCount == -1) {
                if (builder.isEmpty()) {
                    throw IllegalStateException("Remote peer closed the TCP stream.")
                }
                break
            }

            builder.append(buffer, 0, readCount)

            if (builder.contains('\n')) {
                break
            }

            // XMPP frames commonly do not end with '\n'. Once no additional bytes are
            // buffered, return promptly instead of waiting for socket timeout.
            if (!currentReader.ready()) {
                break
            }
        }

        return builder.toString().trimEnd('\n', '\r')
    }

    override suspend fun close() {
        val closeError = runCatching { reader?.close() }.exceptionOrNull()
            ?: runCatching { writer?.close() }.exceptionOrNull()
            ?: runCatching { socket?.close() }.exceptionOrNull()

        reader = null
        writer = null
        socket = null

        if (closeError != null) {
            throw closeError
        }
    }
}

public fun createJvmTcpXmppTransport(): XmppTransport =
    DefaultXmppTransport(socket = JvmTcpTransportSocket())

private const val DEFAULT_READ_TIMEOUT_MILLIS: Int = 1_500
