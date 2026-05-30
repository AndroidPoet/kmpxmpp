package io.github.androidpoet.kmpxmpp.xep0085.chatstates

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppChatStateServiceTest {
    @Test
    fun test_sendState_whenComposing_sendsComposingStanza() = runTest {
        val client = FakeChatStateClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppChatStateService(client)

        val result = service.sendState(
            to = Jid(local = "alice", domain = "example.com"),
            state = XmppChatState.Composing,
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<composing"))
        assertTrue(client.lastStanza!!.contains("http://jabber.org/protocol/chatstates"))
    }

    @Test
    fun test_sendState_whenDomainBlank_returnsInvalidInputFailure() = runTest {
        val service = DefaultXmppChatStateService(FakeChatStateClient(sendResult = XmppResult.Success(Unit)))

        val result = service.sendState(
            to = Jid(local = "alice", domain = " "),
            state = XmppChatState.Active,
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("Recipient JID domain cannot be blank.", result.error.message)
    }
}

private class FakeChatStateClient(
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
