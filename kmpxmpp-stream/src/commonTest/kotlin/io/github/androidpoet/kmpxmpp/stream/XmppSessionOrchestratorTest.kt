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
            transport = FakeTransport(),
            featuresXmlProvider = { validFeaturesXml() },
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
            featuresXmlProvider = { validFeaturesXml() },
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
            transport = FakeTransport(),
            featuresXmlProvider = { "<bad/>" },
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Failure>(result)
        assertEquals("Missing <stream:features> root element.", result.error.message)
    }

    @Test
    fun test_orchestrator_start_whenFeaturesProviderThrows_returnsFailure() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(host = "chat.example.com"),
            transport = FakeTransport(),
            featuresXmlProvider = { error("features-provider-crash") },
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Failure>(result)
        assertEquals("features-provider-crash", result.error.message)
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
            transport = FakeTransport(),
            featuresXmlProvider = { plainOnlyFeaturesXml() },
        )

        val result = orchestrator.start()

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL PLAIN without TLS is disabled by policy.", result.error.message)
        assertEquals(XmppStreamState.TlsUpgraded, orchestrator.state)
    }

    @Test
    fun test_orchestrator_stop_whenReady_transitionsToDisconnected() = runTest {
        val transport = FakeTransport()
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(host = "chat.example.com", tlsInitiallyActive = true),
            transport = transport,
            featuresXmlProvider = { validFeaturesXml() },
        )
        orchestrator.start()

        val result = orchestrator.stop()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(XmppStreamState.Disconnected, orchestrator.state)
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
    private val closeResult: XmppResult<Unit> = XmppResult.Success(Unit),
) : XmppTransport {
    override suspend fun connect(host: String, port: Int): XmppResult<Unit> = connectResult

    override suspend fun write(data: String): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun read(): XmppResult<String> = XmppResult.Success("<ok/>")

    override suspend fun close(): XmppResult<Unit> = closeResult
}
