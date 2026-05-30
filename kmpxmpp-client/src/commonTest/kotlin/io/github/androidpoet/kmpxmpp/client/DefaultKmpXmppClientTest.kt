package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.sasl.SaslAuthenticationService
import io.github.androidpoet.kmpxmpp.sasl.SaslMechanism
import io.github.androidpoet.kmpxmpp.security.SecurityPolicy
import io.github.androidpoet.kmpxmpp.security.TlsMode
import io.github.androidpoet.kmpxmpp.stream.XmppSessionContext
import io.github.androidpoet.kmpxmpp.stream.XmppStreamEngine
import io.github.androidpoet.kmpxmpp.stream.XmppStreamState
import io.github.androidpoet.kmpxmpp.transport.XmppTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultKmpXmppClientTest {
    @Test
    fun test_client_connect_whenStreamStarts_returnsSuccess() = runTest {
        val client = DefaultKmpXmppClient(
            streamEngine = FakeStreamEngine(startResult = XmppResult.Success(Unit), stateAfterStart = XmppStreamState.Ready),
            transport = FakeTransport(),
        )

        val result = client.connect()

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_client_authenticate_whenNotReady_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Connected,
        )
        val client = DefaultKmpXmppClient(streamEngine = stream, transport = FakeTransport())

        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Cannot authenticate before stream is ready.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenSessionContextMissing_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = null,
        )
        val client = DefaultKmpXmppClient(streamEngine = stream, transport = FakeTransport())

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Missing stream session context.", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenAuthServiceFails_propagatesFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            saslAuthenticationService = FakeAuthService(
                result = XmppResult.Failure(XmppError("auth-service-failed")),
            ),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("auth-service-failed", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenAuthServiceThrows_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            saslAuthenticationService = ThrowingAuthService(),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("auth-service-crash", result.error.message)
    }

    @Test
    fun test_client_authenticate_whenPolicyRejectsMechanism_propagatesFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
            contextAfterStart = XmppSessionContext(
                tlsActive = false,
                serverMechanisms = setOf(SaslMechanism.Plain),
            ),
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            securityPolicy = SecurityPolicy(
                tlsMode = TlsMode.Required,
                allowPlainAuthWithoutTls = false,
            ),
            saslAuthenticationService = FakeAuthService(
                result = XmppResult.Success(SaslMechanism.Plain),
            ),
        )

        client.connect()
        val result = client.authenticate(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("SASL PLAIN without TLS is disabled by policy.", result.error.message)
    }

    @Test
    fun test_client_sendStanza_whenNotAuthenticated_returnsFailure() = runTest {
        val client = DefaultKmpXmppClient(
            streamEngine = FakeStreamEngine(startResult = XmppResult.Success(Unit), stateAfterStart = XmppStreamState.Ready),
            transport = FakeTransport(),
        )

        val result = client.sendStanza("<message/>")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Cannot send stanza before authentication.", result.error.message)
    }

    @Test
    fun test_client_sendStanza_whenAuthenticated_propagatesTransportResult() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val transport = FakeTransport(writeResult = XmppResult.Failure(XmppError("write-failed")))
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = transport,
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.ScramSha256)),
        )

        client.connect()
        client.authenticate(Jid("alice", "example.com"), "secret")
        val result = client.sendStanza("<message/>")

        assertIs<XmppResult.Failure>(result)
        assertEquals("write-failed", result.error.message)
    }

    @Test
    fun test_client_sendStanza_whenTransportThrows_returnsFailure() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = ThrowingTransport(),
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.ScramSha256)),
        )

        client.connect()
        client.authenticate(Jid("alice", "example.com"), "secret")
        val result = client.sendStanza("<message/>")

        assertIs<XmppResult.Failure>(result)
        assertEquals("transport-write-crash", result.error.message)
    }

    @Test
    fun test_client_disconnect_whenCalled_clearsSessionAndReturnsSuccess() = runTest {
        val stream = FakeStreamEngine(
            startResult = XmppResult.Success(Unit),
            stopResult = XmppResult.Success(Unit),
            stateAfterStart = XmppStreamState.Ready,
        )
        val client = DefaultKmpXmppClient(
            streamEngine = stream,
            transport = FakeTransport(),
            saslAuthenticationService = FakeAuthService(XmppResult.Success(SaslMechanism.ScramSha256)),
        )

        client.connect()
        client.authenticate(Jid("alice", "example.com"), "secret")
        val disconnectResult = client.disconnect()
        val sendAfterDisconnect = client.sendStanza("<message/>")

        assertIs<XmppResult.Success<Unit>>(disconnectResult)
        assertIs<XmppResult.Failure>(sendAfterDisconnect)
        assertEquals("Cannot send stanza before authentication.", sendAfterDisconnect.error.message)
    }
}

private class FakeAuthService(
    private val result: XmppResult<SaslMechanism>,
) : SaslAuthenticationService {
    override suspend fun authenticate(
        jid: Jid,
        password: String,
        tlsActive: Boolean,
        serverMechanisms: Set<SaslMechanism>,
    ): XmppResult<SaslMechanism> = result
}

private class ThrowingAuthService : SaslAuthenticationService {
    override suspend fun authenticate(
        jid: Jid,
        password: String,
        tlsActive: Boolean,
        serverMechanisms: Set<SaslMechanism>,
    ): XmppResult<SaslMechanism> = error("auth-service-crash")
}

private class FakeStreamEngine(
    private val startResult: XmppResult<Unit>,
    private val stopResult: XmppResult<Unit> = XmppResult.Success(Unit),
    private val stateAfterStart: XmppStreamState,
    private val contextAfterStart: XmppSessionContext? = XmppSessionContext(
        tlsActive = true,
        serverMechanisms = setOf(SaslMechanism.ScramSha256, SaslMechanism.Plain),
    ),
) : XmppStreamEngine {
    override var state: XmppStreamState = XmppStreamState.Disconnected
    override var sessionContext: XmppSessionContext? = null

    override suspend fun start(): XmppResult<Unit> {
        if (startResult is XmppResult.Success) {
            state = stateAfterStart
            sessionContext = contextAfterStart
        }
        return startResult
    }

    override suspend fun stop(): XmppResult<Unit> {
        if (stopResult is XmppResult.Success) {
            state = XmppStreamState.Disconnected
            sessionContext = null
        }
        return stopResult
    }
}

private class FakeTransport(
    private val writeResult: XmppResult<Unit> = XmppResult.Success(Unit),
) : XmppTransport {
    override suspend fun connect(host: String, port: Int): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun write(data: String): XmppResult<Unit> = writeResult

    override suspend fun read(): XmppResult<String> = XmppResult.Success("<ok/>")

    override suspend fun close(): XmppResult<Unit> = XmppResult.Success(Unit)
}

private class ThrowingTransport : XmppTransport {
    override suspend fun connect(host: String, port: Int): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun write(data: String): XmppResult<Unit> = error("transport-write-crash")

    override suspend fun read(): XmppResult<String> = XmppResult.Success("<ok/>")

    override suspend fun close(): XmppResult<Unit> = XmppResult.Success(Unit)
}
