package io.github.androidpoet.kmpxmpp.tcp

import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import java.net.Socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmTcpTransportSocketTest {
    @Test
    fun test_transport_whenConnected_canWriteAndRead() = runBlocking {
        val server = ServerSocket(0)
        val port = server.localPort

        val serverJob = async(Dispatchers.IO) {
            server.use {
                val incoming = it.accept()
                handleEcho(incoming)
            }
        }

        val transport = createJvmTcpXmppTransport()

        val connectResult = transport.connect("127.0.0.1", port)
        assertIs<XmppResult.Success<Unit>>(connectResult)

        val writeResult = transport.write("ping\n")
        assertIs<XmppResult.Success<Unit>>(writeResult)

        val readResult = transport.read()
        assertIs<XmppResult.Success<String>>(readResult)
        assertEquals("pong", readResult.value)

        val closeResult = transport.close()
        assertIs<XmppResult.Success<Unit>>(closeResult)

        serverJob.await()
    }

    @Test
    fun test_transport_whenPortClosed_connectReturnsFailure() = runBlocking {
        val reservedPort = ServerSocket(0).use { it.localPort }
        val transport = createJvmTcpXmppTransport()

        val result = transport.connect("127.0.0.1", reservedPort)

        assertIs<XmppResult.Failure>(result)
        assertTrue(result.error.message.isNotBlank())
    }
}

private fun handleEcho(socket: Socket) {
    socket.use {
        val reader = it.getInputStream().bufferedReader(Charsets.UTF_8)
        val writer = it.getOutputStream().bufferedWriter(Charsets.UTF_8)
        val line = reader.readLine()
        if (line == "ping") {
            writer.write("pong\n")
            writer.flush()
        }
    }
}
