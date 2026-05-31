package io.github.androidpoet.kmpxmpp.reconnect

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import io.github.androidpoet.kmpxmpp.core.XmppRetryPolicy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultXmppReconnectManagerTest {
    @Test
    fun test_reconnect_whenFirstConnectFailsAndSecondSucceeds_returnsSuccess() = runTest {
        val client = FakeClient(
            connectResults = mutableListOf(
                XmppResult.Failure(
                    XmppError(
                        message = "temporary-connect-fail",
                        code = XmppErrorCode.TransportFailure,
                        stage = XmppErrorStage.Connect,
                        recoverable = true,
                    ),
                ),
                XmppResult.Success(Unit),
            ),
        )
        val manager = DefaultXmppReconnectManager(
            client = client,
            retryPolicy = XmppRetryPolicy(maxAttempts = 2),
        )

        val result = manager.reconnect()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(2, client.connectCalls)
    }

    @Test
    fun test_reconnect_whenFailureNonRecoverable_stopsEarly() = runTest {
        val client = FakeClient(
            connectResults = mutableListOf(
                XmppResult.Failure(
                    XmppError(
                        message = "fatal-connect-fail",
                        code = XmppErrorCode.InvalidState,
                        stage = XmppErrorStage.Connect,
                        recoverable = false,
                    ),
                ),
            ),
        )
        val manager = DefaultXmppReconnectManager(
            client = client,
            retryPolicy = XmppRetryPolicy(maxAttempts = 5),
        )

        val result = manager.reconnect()

        assertIs<XmppResult.Failure>(result)
        assertEquals("fatal-connect-fail", result.error.message)
        assertEquals(1, client.connectCalls)
    }

    @Test
    fun test_reconnectWithAuthentication_whenConnectAndAuthSucceed_returnsSuccess() = runTest {
        val client = FakeClient(
            connectResults = mutableListOf(XmppResult.Success(Unit)),
            authResults = mutableListOf(XmppResult.Success(Unit)),
        )
        val manager = DefaultXmppReconnectManager(client = client)

        val result = manager.reconnectWithAuthentication(
            XmppReconnectCredentials(
                jid = Jid(local = "alice", domain = "example.com"),
                password = "secret",
            ),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.connectCalls)
        assertEquals(1, client.authCalls)
    }

    @Test
    fun test_reconnectWithAuthentication_whenAuthFailsAndRecoverable_retriesFullFlow() = runTest {
        val client = FakeClient(
            connectResults = mutableListOf(
                XmppResult.Success(Unit),
                XmppResult.Success(Unit),
            ),
            authResults = mutableListOf(
                XmppResult.Failure(
                    XmppError(
                        message = "auth-temp-fail",
                        code = XmppErrorCode.AuthenticationFailed,
                        stage = XmppErrorStage.Authentication,
                        recoverable = true,
                    ),
                ),
                XmppResult.Success(Unit),
            ),
        )
        val manager = DefaultXmppReconnectManager(
            client = client,
            retryPolicy = XmppRetryPolicy(maxAttempts = 2),
        )

        val result = manager.reconnectWithAuthentication(
            XmppReconnectCredentials(
                jid = Jid(local = "alice", domain = "example.com"),
                password = "secret",
            ),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(2, client.connectCalls)
        assertEquals(2, client.authCalls)
    }
}

private class FakeClient(
    private val connectResults: MutableList<XmppResult<Unit>>,
    private val authResults: MutableList<XmppResult<Unit>> = mutableListOf(XmppResult.Success(Unit)),
) : KmpXmppClient {
    var connectCalls: Int = 0
    var authCalls: Int = 0

    override suspend fun connect(): XmppResult<Unit> {
        connectCalls += 1
        return connectResults.removeFirstOrNull() ?: XmppResult.Success(Unit)
    }

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> {
        authCalls += 1
        return authResults.removeFirstOrNull() ?: XmppResult.Success(Unit)
    }

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun disconnect(): XmppResult<Unit> = XmppResult.Success(Unit)
}
