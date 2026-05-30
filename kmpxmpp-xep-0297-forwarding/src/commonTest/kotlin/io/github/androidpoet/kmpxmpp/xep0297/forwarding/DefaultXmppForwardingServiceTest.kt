package io.github.androidpoet.kmpxmpp.xep0297.forwarding

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppForwardingServiceTest {
    @Test
    fun test_forwardMessage_whenValidInput_sendsForwardedWrapper() = runTest {
        val client = FakeForwardingClient(XmppResult.Success(Unit))
        val service = DefaultXmppForwardingService(client)

        val result = service.forwardMessage(
            to = Jid(local = "b", domain = "example.com"),
            forwardedMessageXml = "<message from='a@example.com'><body>hi</body></message>",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("forwarded"))
        assertTrue(client.lastStanza!!.contains("urn:xmpp:forward:0"))
    }

    @Test
    fun test_forwardMessage_whenPayloadBlank_returnsFailure() = runTest {
        val service = DefaultXmppForwardingService(FakeForwardingClient(XmppResult.Success(Unit)))

        val result = service.forwardMessage(Jid(local = "b", domain = "example.com"), " ")

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }
}

private class FakeForwardingClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
