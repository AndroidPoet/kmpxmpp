package io.github.androidpoet.kmpxmpp.stream

import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
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
    fun test_orchestrator_start_whenPlainWithoutTlsPolicyBlocked_returnsFailure() = runTest {
        val orchestrator = XmppSessionOrchestrator(
            config = XmppSessionConfig(
                host = "chat.example.com",
                tlsInitiallyActive = false,
                mechanism = SaslMechanism.Plain,
                securityPolicy = SecurityPolicy(
                    tlsMode = TlsMode.Disabled,
                    allowPlainAuthWithoutTls = false,
                ),
            ),
            transport = FakeTransport(),
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
        )
        orchestrator.start()

        val result = orchestrator.stop()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(XmppStreamState.Disconnected, orchestrator.state)
    }
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
