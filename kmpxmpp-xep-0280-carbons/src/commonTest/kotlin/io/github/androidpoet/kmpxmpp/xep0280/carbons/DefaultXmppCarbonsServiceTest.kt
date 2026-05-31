package io.github.androidpoet.kmpxmpp.xep0280.carbons

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppCarbonsServiceTest {
    @Test
    fun test_enableCarbons_whenCalled_sendsEnableIq() = runTest {
        val client = FakeCarbonsClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppCarbonsService(client)

        val result = service.enableCarbons()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<enable"))
        assertTrue(client.lastStanza!!.contains("urn:xmpp:carbons:2"))
    }

    @Test
    fun test_disableCarbons_whenCalled_sendsDisableIq() = runTest {
        val client = FakeCarbonsClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppCarbonsService(client)

        val result = service.disableCarbons()

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("<disable"))
    }

    @Test
    fun test_parseForwardedCarbon_whenReceivedCarbonValid_returnsParsedMessage() {
        val service = DefaultXmppCarbonsService(FakeCarbonsClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <message from='alice@example.com'>
              <received xmlns='urn:xmpp:carbons:2'>
                <forwarded xmlns='urn:xmpp:forward:0'>
                  <message from='alice@example.com' to='bob@example.com'>
                    <body>hello</body>
                  </message>
                </forwarded>
              </received>
            </message>
        """.trimIndent()

        val parsed = service.parseForwardedCarbon(xml)

        assertIs<XmppResult.Success<XmppCarbonMessage>>(parsed)
        assertEquals(CarbonDirection.Received, parsed.value.direction)
        assertEquals("alice", parsed.value.from?.local)
        assertEquals("bob", parsed.value.to?.local)
        assertEquals("hello", parsed.value.body)
    }

    @Test
    fun test_parseForwardedCarbon_whenPrefixedTagsPresent_returnsParsedMessage() {
        val service = DefaultXmppCarbonsService(FakeCarbonsClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <message from='alice@example.com'>
              <c:received xmlns:c='urn:xmpp:carbons:2'>
                <f:forwarded xmlns:f='urn:xmpp:forward:0'>
                  <message from='alice@example.com' to='bob@example.com'>
                    <b:body xmlns:b='jabber:client'>hello-prefixed</b:body>
                  </message>
                </f:forwarded>
              </c:received>
            </message>
        """.trimIndent()

        val parsed = service.parseForwardedCarbon(xml)

        assertIs<XmppResult.Success<XmppCarbonMessage>>(parsed)
        assertEquals(CarbonDirection.Received, parsed.value.direction)
        assertEquals("alice", parsed.value.from?.local)
        assertEquals("bob", parsed.value.to?.local)
        assertEquals("hello-prefixed", parsed.value.body)
    }

    @Test
    fun test_parseForwardedCarbon_whenForwardedMissing_returnsFailure() {
        val service = DefaultXmppCarbonsService(FakeCarbonsClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<message><sent xmlns='urn:xmpp:carbons:2'/></message>"

        val parsed = service.parseForwardedCarbon(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Carbon stanza missing forwarded payload.", parsed.error.message)
    }

    @Test
    fun test_validateCarbonsIqResult_whenMatchingId_returnsSuccess() {
        val service = DefaultXmppCarbonsService(FakeCarbonsClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<iq type='result' id='carbons-enable'/>"

        val result = service.validateCarbonsIqResult(xml, requestId = "carbons-enable")

        assertIs<XmppResult.Success<Unit>>(result)
    }

    @Test
    fun test_validateCarbonsIqResult_whenIdMismatch_returnsFailure() {
        val service = DefaultXmppCarbonsService(FakeCarbonsClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<iq type='result' id='wrong-id'/>"

        val result = service.validateCarbonsIqResult(xml, requestId = "carbons-enable")

        assertIs<XmppResult.Failure>(result)
        assertEquals("Carbons IQ result stanza missing matching id/type result.", result.error.message)
    }

    @Test
    fun test_validateCarbonsIqResult_whenPrefixedIqAndAttributesOutOfOrder_returnsSuccess() {
        val service = DefaultXmppCarbonsService(FakeCarbonsClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<s:iq id='carbons-enable' xmlns:s='jabber:client' type='result'/>"

        val result = service.validateCarbonsIqResult(xml, requestId = "carbons-enable")

        assertIs<XmppResult.Success<Unit>>(result)
    }
}

private class FakeCarbonsClient(
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
