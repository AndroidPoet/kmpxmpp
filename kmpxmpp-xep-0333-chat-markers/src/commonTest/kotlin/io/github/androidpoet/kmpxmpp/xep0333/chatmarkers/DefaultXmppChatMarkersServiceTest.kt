package io.github.androidpoet.kmpxmpp.xep0333.chatmarkers

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppErrorStage
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppChatMarkersServiceTest {
    @Test
    fun test_markDisplayed_whenValidInput_sendsDisplayedMarkerStanza() = runTest {
        val client = FakeChatMarkersClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppChatMarkersService(client)

        val result = service.markDisplayed(
            to = Jid(local = "alice", domain = "example.com"),
            messageId = "m-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<displayed"))
        assertTrue(client.lastStanza!!.contains("urn:xmpp:chat-markers:0"))
    }

    @Test
    fun test_markReceived_whenMessageIdBlank_returnsInvalidInput() = runTest {
        val service = DefaultXmppChatMarkersService(FakeChatMarkersClient(sendResult = XmppResult.Success(Unit)))

        val result = service.markReceived(
            to = Jid(local = "alice", domain = "example.com"),
            messageId = " ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals(XmppErrorStage.Messaging, result.error.stage)
        assertEquals("Message id cannot be blank.", result.error.message)
    }

    @Test
    fun test_parseChatMarker_whenDisplayedMarkerValid_returnsParsedMarker() {
        val service = DefaultXmppChatMarkersService(FakeChatMarkersClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message from='alice@example.com'><displayed xmlns='urn:xmpp:chat-markers:0' id='m-77'/></message>"

        val parsed = service.parseChatMarker(xml)

        assertIs<XmppResult.Success<ParsedChatMarker>>(parsed)
        assertEquals(ChatMarkerType.Displayed, parsed.value.type)
        assertEquals("m-77", parsed.value.messageId)
        assertEquals("alice", parsed.value.from?.local)
    }

    @Test
    fun test_parseChatMarker_whenNamespaceMissing_returnsFailure() {
        val service = DefaultXmppChatMarkersService(FakeChatMarkersClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message><displayed id='m-77'/></message>"

        val parsed = service.parseChatMarker(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Chat marker stanza missing urn:xmpp:chat-markers:0 namespace.", parsed.error.message)
    }

    @Test
    fun test_parseChatMarker_whenIdMissing_returnsFailure() {
        val service = DefaultXmppChatMarkersService(FakeChatMarkersClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message><received xmlns='urn:xmpp:chat-markers:0'/></message>"

        val parsed = service.parseChatMarker(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Chat marker element missing id attribute.", parsed.error.message)
    }
}

private class FakeChatMarkersClient(
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
