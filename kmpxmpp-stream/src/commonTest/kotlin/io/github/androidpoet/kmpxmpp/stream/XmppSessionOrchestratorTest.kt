package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.security.SecurityPolicy
import io.github.androidpoet.kmpxmpp.security.TlsMode
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class XmppSessionOrchestratorTest {
    @Test
    fun test_orchestrator_start_whenTransportAndPolicyPass_reachesReady() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(
                host = "chat.example.com",
                tlsInitiallyActive = true,
            ),
            transport = FakeTransport(readResults = listOf(XmppResult.Success(validFeaturesXml()))),
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(XmppStreamState.Ready, orchestrator.state)
    }

    @Test
    fun test_orchestrator_start_whenTransportFails_returnsFailureAndStaysDisconnected() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(host = "chat.example.com"),
            transport = FakeTransport(connectResult = XmppResult.Failure(XmppError("connect-failed"))),
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Failure>(result)
        assertEquals("connect-failed", result.error.message)
        assertEquals(XmppStreamState.Disconnected, orchestrator.state)
    }

    @Test
    fun test_orchestrator_start_whenFeaturesInvalid_returnsFailure() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(host = "chat.example.com"),
            transport = FakeTransport(readResults = listOf(XmppResult.Success("<bad/>"))),
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Failure>(result)
        assertEquals("Unable to extract <stream:features> from server response.", result.error.message)
    }

    @Test
    fun test_orchestrator_start_whenTransportReadFails_returnsFailure() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(host = "chat.example.com"),
            transport = FakeTransport(readResults = listOf(XmppResult.Failure(XmppError("read-failed")))),
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Failure>(result)
        assertEquals("read-failed", result.error.message)
    }

    @Test
    fun test_orchestrator_start_whenPlainWithoutTlsPolicyBlocked_returnsFailure() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(
                host = "chat.example.com",
                tlsInitiallyActive = false,
                securityPolicy = SecurityPolicy(
                    tlsMode = TlsMode.Disabled,
                    allowPlainAuthWithoutTls = false,
                ),
            ),
            transport = FakeTransport(readResults = listOf(XmppResult.Success(plainOnlyFeaturesXml()))),
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL PLAIN without TLS is disabled by policy.", result.error.message)
        assertEquals(XmppStreamState.TlsUpgraded, orchestrator.state)
    }

    @Test
    fun test_orchestrator_stop_whenReady_transitionsToDisconnected() = runTest {
        val transport = FakeTransport(readResults = listOf(XmppResult.Success(validFeaturesXml())))
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(host = "chat.example.com", tlsInitiallyActive = true),
            transport = transport,
        )
        orchestrator.start()

        val result = orchestrator.stop()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(XmppStreamState.Disconnected, orchestrator.state)
    }

    @Test
    fun test_orchestrator_start_whenFeaturesSplitAcrossReads_reachesReady() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(host = "chat.example.com", tlsInitiallyActive = true),
            transport = FakeTransport(
                readResults = listOf(
                    XmppResult.Success("<stream:stream from='chat.example.com'>"),
                    XmppResult.Success(validFeaturesXml()),
                ),
            ),
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(XmppStreamState.Ready, orchestrator.state)
    }

    @Test
    fun test_orchestrator_start_whenFeaturesNeverArrive_returnsParsingFailure() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(host = "chat.example.com", tlsInitiallyActive = true),
            transport = FakeTransport(
                readResults = listOf(
                    XmppResult.Success("<stream:stream from='chat.example.com'>"),
                    XmppResult.Success("<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>"),
                    XmppResult.Success("<challenge xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>abc</challenge>"),
                ),
            ),
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Failure>(result)
        assertEquals("Unable to extract <stream:features> from server response.", result.error.message)
    }

    private fun validFeaturesXml(): String = """
        <stream:features>
          <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
            <mechanism>SCRAM-SHA-256</mechanism>
            <mechanism>PLAIN</mechanism>
          </mechanisms>
        </stream:features>
    """.trimIndent()

    private fun plainOnlyFeaturesXml(): String = """
        <stream:features>
          <mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'>
            <mechanism>PLAIN</mechanism>
          </mechanisms>
        </stream:features>
    """.trimIndent()
}

private class FakeTransport(
    private val connectResult: XmppResult<Unit> = XmppResult.Success(Unit),
    private val writeResult: XmppResult<Unit> = XmppResult.Success(Unit),
    private val readResults: List<XmppResult<String>> = listOf(XmppResult.Success("<ok/>")),
    private val closeResult: XmppResult<Unit> = XmppResult.Success(Unit),
) : XmppTransport {
    var writes: Int = 0
    private var readIndex: Int = 0

    override suspend fun connect(host: String, port: Int): XmppResult<Unit> = connectResult

    override suspend fun write(data: String): XmppResult<Unit> {
        writes += 1
        return writeResult
    }

    override suspend fun read(): XmppResult<String> {
        val result = readResults.getOrNull(readIndex) ?: readResults.last()
        readIndex += 1
        return result
    }

    override suspend fun close(): XmppResult<Unit> = closeResult
}
