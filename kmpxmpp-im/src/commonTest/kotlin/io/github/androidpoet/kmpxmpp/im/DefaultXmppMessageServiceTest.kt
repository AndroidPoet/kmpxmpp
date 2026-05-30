package io.github.androidpoet.kmpxmpp.im

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppError
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppMessageServiceTest {
    @Test
    fun test_sendChatMessage_whenValidMessage_sendsEscapedStanza() = runTest {
        val client = FakeMessageClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppMessageService(client)

        val result = service.sendChatMessage(
            to = Jid(local = "alice", domain = "example.com"),
            body = "Hello <world> & \"team\"",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("&lt;world&gt;"))
        assertTrue(client.lastStanza!!.contains("&amp;"))
        assertTrue(client.lastStanza!!.contains("&quot;team&quot;"))
    }

    @Test
    fun test_sendChatMessage_whenBodyBlank_returnsInvalidInputFailure() = runTest {
        val service = DefaultXmppMessageService(FakeMessageClient(sendResult = XmppResult.Success(Unit)))

        val result = service.sendChatMessage(
            to = Jid(local = "alice", domain = "example.com"),
            body = "  ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals("Message body cannot be blank.", result.error.message)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals(XmppErrorStage.Messaging, result.error.stage)
    }

    @Test
    fun test_sendChatMessage_whenClientSendFails_propagatesFailure() = runTest {
        val client = FakeMessageClient(
            sendResult = XmppResult.Failure(
                XmppError(
                    message = "send-failed",
                    code = XmppErrorCode.TransportFailure,
                    stage = XmppErrorStage.Messaging,
                    recoverable = true,
                ),
            ),
        )
        val service = DefaultXmppMessageService(client)

        val result = service.sendChatMessage(
            to = Jid(local = "alice", domain = "example.com"),
            body = "Hello",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals("send-failed", result.error.message)
        assertEquals(1, client.sendCalls)
    }
}

private class FakeMessageClient(
    private val sendResult: XmppResult<Unit>,
) : KmpXmppClient {
    var sendCalls: Int = 0
    var lastStanza: String? = null

    override suspend fun connect(): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun authenticate(jid: Jid, password: String): XmppResult<Unit> = XmppResult.Success(Unit)

    override suspend fun sendStanza(rawXml: String): XmppResult<Unit> {
        sendCalls += 1
        lastStanza = rawXml
        return sendResult
    }

    override suspend fun disconnect(): XmppResult<Unit> = XmppResult.Success(Unit)
}
