package io.github.androidpoet.kmpxmpp.transport

import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultXmppTransportTest {
    @Test
    fun test_transport_connect_whenSocketSucceeds_returnsSuccess() = runTest {
        val transport = DefaultXmppTransport(socket = FakeSocket())

        val result = transport.connect("chat.example.com", 5222)

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_transport_connect_whenSocketThrows_returnsFailureWithConnectStage() = runTest {
        val transport = DefaultXmppTransport(socket = FakeSocket(connectError = IllegalStateException("connect-crash")))

        val result = transport.connect("chat.example.com", 5222)

        assertIs<XmppResult.Failure>(result)
        assertEquals("connect-crash", result.error.message)
        assertEquals(XmppErrorCode.TransportFailure, result.error.code)
        assertEquals(XmppErrorStage.Connect, result.error.stage)
    }

    @Test
    fun test_transport_write_whenSocketThrows_returnsFailureWithMessagingStage() = runTest {
        val transport = DefaultXmppTransport(socket = FakeSocket(writeError = IllegalStateException("write-crash")))

        val result = transport.write("<message/>")

        assertIs<XmppResult.Failure>(result)
        assertEquals("write-crash", result.error.message)
        assertEquals(XmppErrorCode.TransportFailure, result.error.code)
        assertEquals(XmppErrorStage.Messaging, result.error.stage)
    }

    @Test
    fun test_transport_read_whenSocketThrows_returnsFailureWithMessagingStage() = runTest {
        val transport = DefaultXmppTransport(socket = FakeSocket(readError = IllegalStateException("read-crash")))

        val result = transport.read()

        assertIs<XmppResult.Failure>(result)
        assertEquals("read-crash", result.error.message)
        assertEquals(XmppErrorCode.TransportFailure, result.error.code)
        assertEquals(XmppErrorStage.Messaging, result.error.stage)
    }

    @Test
    fun test_transport_close_whenSocketThrows_returnsFailureWithDisconnectStage() = runTest {
        val transport = DefaultXmppTransport(socket = FakeSocket(closeError = IllegalStateException("close-crash")))

        val result = transport.close()

        assertIs<XmppResult.Failure>(result)
        assertEquals("close-crash", result.error.message)
        assertEquals(XmppErrorCode.TransportFailure, result.error.code)
        assertEquals(XmppErrorStage.Disconnect, result.error.stage)
    }
}

private class FakeSocket(
    private val connectError: Throwable? = null,
    private val writeError: Throwable? = null,
    private val readError: Throwable? = null,
    private val closeError: Throwable? = null,
) : TransportSocket {
    override suspend fun connect(host: String, port: Int) {
        connectError?.let { throw it }
    }

    override suspend fun write(data: String) {
        writeError?.let { throw it }
    }

    override suspend fun read(): String {
        readError?.let { throw it }
        return "<ok/>"
    }

    override suspend fun close() {
        closeError?.let { throw it }
    }
}
