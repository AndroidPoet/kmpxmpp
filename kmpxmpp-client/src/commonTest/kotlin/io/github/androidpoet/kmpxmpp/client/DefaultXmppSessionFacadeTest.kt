package io.github.androidpoet.kmpxmpp.client

import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultXmppSessionFacadeTest {
    @Test
    fun test_openSession_whenConnectAndAuthSucceed_returnsSuccess() = runTest {
        val client = FakeSessionClient(
            connectResult = XmppResult.Success(Unit),
            authResult = XmppResult.Success(Unit),
        )
        val facade = DefaultXmppSessionFacade(client)

        val result = facade.openSession(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.connectCalls)
        assertEquals(1, client.authCalls)
    }

    @Test
    fun test_openSession_whenConnectFails_propagatesFailureAndSkipsAuth() = runTest {
        val client = FakeSessionClient(
            connectResult = XmppResult.Failure(
                XmppError(
                    message = "connect-fail",
                    code = XmppErrorCode.TransportFailure,
                    stage = XmppErrorStage.Connect,
                    recoverable = true,
                ),
            ),
            authResult = XmppResult.Success(Unit),
        )
        val facade = DefaultXmppSessionFacade(client)

        val result = facade.openSession(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("connect-fail", result.error.message)
        assertEquals(1, client.connectCalls)
        assertEquals(0, client.authCalls)
    }

    @Test
    fun test_openSession_whenAuthFails_propagatesFailure() = runTest {
        val client = FakeSessionClient(
            connectResult = XmppResult.Success(Unit),
            authResult = XmppResult.Failure(
                XmppError(
                    message = "auth-fail",
                    code = XmppErrorCode.AuthenticationFailed,
                    stage = XmppErrorStage.Authentication,
                    recoverable = true,
                ),
            ),
        )
        val facade = DefaultXmppSessionFacade(client)

        val result = facade.openSession(Jid("alice", "example.com"), "secret")

        assertIs<XmppResult.Failure>(result)
        assertEquals("auth-fail", result.error.message)
        assertEquals(1, client.connectCalls)
        assertEquals(1, client.authCalls)
    }

    @Test
    fun test_closeSession_whenDisconnectCalled_returnsDisconnectResult() = runTest {
        val client = FakeSessionClient(
            connectResult = XmppResult.Success(Unit),
            authResult = XmppResult.Success(Unit),
            disconnectResult = XmppResult.Failure(
                XmppError(
                    message = "disconnect-fail",
                    code = XmppErrorCode.TransportFailure,
                    stage = XmppErrorStage.Disconnect,
                    recoverable = true,
                ),
            ),
        )
        val facade = DefaultXmppSessionFacade(client)

        val result = facade.closeSession()

        assertIs<XmppResult.Failure>(result)
        assertEquals("disconnect-fail", result.error.message)
        assertEquals(1, client.disconnectCalls)
    }
}

private class FakeSessionClient(
    private val connectResult: XmppResult<Unit>,
    private val authResult: XmppResult<Unit>,
    private val disconnectResult: XmppResult<Unit> = XmppResult.Success(Unit),
) : KmpXmppClient {
    var connectCalls: Int = 0
    var authCalls: Int = 0
    var disconnectCalls: Int = 0

    override suspend fun connect(): XmppResult<Unit> {
        connectCalls += 1
        return connectResult
    }

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> {
        authCalls += 1
        return authResult
    }

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun disconnect(): XmppResult<Unit> {
        disconnectCalls += 1
        return disconnectResult
    }
}
