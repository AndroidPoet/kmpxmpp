package io.github.androidpoet.kmpxmpp.xep0184.receipts

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

class DefaultXmppReceiptServiceTest {
    @Test
    fun test_sendMessageWithReceiptRequest_whenValidInput_sendsReceiptRequestStanza() = runTest {
        val client = FakeReceiptClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppReceiptService(client)

        val result = service.sendMessageWithReceiptRequest(
            to = Jid(local = "alice", domain = "example.com"),
            body = "hi & <there>",
            messageId = "msg-1",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("xmlns='urn:xmpp:receipts'"))
        assertTrue(client.lastStanza!!.contains("<request"))
        assertTrue(client.lastStanza!!.contains("&lt;there&gt;"))
    }

    @Test
    fun test_sendMessageWithReceiptRequest_whenMessageIdBlank_returnsFailure() = runTest {
        val service = DefaultXmppReceiptService(FakeReceiptClient(sendResult = XmppResult.Success(Unit)))

        val result = service.sendMessageWithReceiptRequest(
            to = Jid(local = "alice", domain = "example.com"),
            body = "hello",
            messageId = " ",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals(XmppErrorStage.Messaging, result.error.stage)
        assertEquals("Message id cannot be blank.", result.error.message)
    }

    @Test
    fun test_sendReceivedReceipt_whenValidInput_sendsReceivedStanza() = runTest {
        val client = FakeReceiptClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppReceiptService(client)

        val result = service.sendReceivedReceipt(
            to = Jid(local = "bob", domain = "example.com"),
            receiptId = "m-22",
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<received"))
        assertTrue(client.lastStanza!!.contains("id='m-22'"))
    }

    @Test
    fun test_parseReceiptRequest_whenValidXml_returnsParsedRequest() {
        val service = DefaultXmppReceiptService(FakeReceiptClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message from='alice@example.com' id='m1'><body>hi</body><request xmlns='urn:xmpp:receipts'/></message>"

        val parsed = service.parseReceiptRequest(xml)

        assertIs<XmppResult.Success<XmppReceiptRequest>>(parsed)
        assertEquals("m1", parsed.value.messageId)
        assertEquals("alice", parsed.value.from?.local)
        assertEquals("example.com", parsed.value.from?.domain)
    }

    @Test
    fun test_parseReceiptRequest_whenRequestMissing_returnsFailure() {
        val service = DefaultXmppReceiptService(FakeReceiptClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message from='alice@example.com' id='m1'><body>hi</body></message>"

        val parsed = service.parseReceiptRequest(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Receipt stanza missing urn:xmpp:receipts namespace.", parsed.error.message)
    }

    @Test
    fun test_parseReceivedReceipt_whenValidXml_returnsParsedReceipt() {
        val service = DefaultXmppReceiptService(FakeReceiptClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message from='bob@example.com'><received xmlns='urn:xmpp:receipts' id='m-22'/></message>"

        val parsed = service.parseReceivedReceipt(xml)

        assertIs<XmppResult.Success<XmppReceivedReceipt>>(parsed)
        assertEquals("m-22", parsed.value.receiptId)
        assertEquals("bob", parsed.value.from?.local)
        assertEquals("example.com", parsed.value.from?.domain)
    }

    @Test
    fun test_parseReceivedReceipt_whenIdMissing_returnsFailure() {
        val service = DefaultXmppReceiptService(FakeReceiptClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message><received xmlns='urn:xmpp:receipts'/></message>"

        val parsed = service.parseReceivedReceipt(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Received receipt stanza missing id attribute.", parsed.error.message)
    }
}

private class FakeReceiptClient(
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
