package io.github.androidpoet.kmpxmpp.websocket

import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class JvmWebSocketTransportSocketTest {
    @Test
    fun test_transport_whenServerUnavailable_connectReturnsFailure() = runBlocking {
        val transport = createJvmWebSocketXmppTransport(
            path = "/xmpp-websocket",
            secure = false,
            readTimeoutMillis = 500,
        )

        val result = transport.connect("127.0.0.1", 1)

        assertIs<XmppResult.Failure>(result)
        assertTrue(result.error.message.isNotBlank())
    }

    @Test
    fun test_transport_whenNotConnected_writeReturnsFailure() = runBlocking {
        val transport = createJvmWebSocketXmppTransport(readTimeoutMillis = 500)

        val result = transport.write("<message/>")

        assertIs<XmppResult.Failure>(result)
        assertTrue(result.error.message.contains("not connected", ignoreCase = true))
    }

    @Test
    fun test_transport_whenNotConnected_readReturnsFailure() = runBlocking {
        val transport = createJvmWebSocketXmppTransport(readTimeoutMillis = 500)

        val result = transport.read()

        assertIs<XmppResult.Failure>(result)
        assertTrue(result.error.message.contains("not connected", ignoreCase = true))
    }
}
