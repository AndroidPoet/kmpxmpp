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

    @Test
    fun test_parseForwardedMessage_whenValidEnvelope_returnsMessageXml() {
        val service = DefaultXmppForwardingService(FakeForwardingClient(XmppResult.Success(Unit)))
        val xml = """
            <message from='a@example.com'>
              <forwarded xmlns='urn:xmpp:forward:0'>
                <message from='a@example.com' to='b@example.com'><body>hi</body></message>
              </forwarded>
            </message>
        """.trimIndent()

        val parsed = service.parseForwardedMessage(xml)

        assertIs<XmppResult.Success<ParsedForwardedMessage>>(parsed)
        assertTrue(parsed.value.messageXml.contains("<body>hi</body>"))
    }

    @Test
    fun test_parseForwardedMessage_whenPrefixedForwardedTag_returnsMessageXml() {
        val service = DefaultXmppForwardingService(FakeForwardingClient(XmppResult.Success(Unit)))
        val xml = """
            <message from='a@example.com'>
              <f:forwarded xmlns:f='urn:xmpp:forward:0'>
                <message from='a@example.com' to='b@example.com'><b:body xmlns:b='jabber:client'>prefixed</b:body></message>
              </f:forwarded>
            </message>
        """.trimIndent()

        val parsed = service.parseForwardedMessage(xml)

        assertIs<XmppResult.Success<ParsedForwardedMessage>>(parsed)
        assertTrue(parsed.value.messageXml.contains("prefixed"))
    }

    @Test
    fun test_parseForwardedMessage_whenMissingForwardedNamespace_returnsFailure() {
        val service = DefaultXmppForwardingService(FakeForwardingClient(XmppResult.Success(Unit)))
        val xml = "<message><forwarded><message><body>hi</body></message></forwarded></message>"

        val parsed = service.parseForwardedMessage(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validateForwardedEnvelope_whenValidEnvelope_returnsSuccess() {
        val service = DefaultXmppForwardingService(FakeForwardingClient(XmppResult.Success(Unit)))
        val xml = "<message><forwarded xmlns='urn:xmpp:forward:0'><message><body>ok</body></message></forwarded></message>"

        val validated = service.validateForwardedEnvelope(xml)

        assertIs<XmppResult.Success<Unit>>(validated)
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
