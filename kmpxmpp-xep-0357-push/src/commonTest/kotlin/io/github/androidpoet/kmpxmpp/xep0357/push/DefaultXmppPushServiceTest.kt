package io.github.androidpoet.kmpxmpp.xep0357.push

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppPushServiceTest {
    @Test
    fun test_enablePush_whenValidInput_sendsEnableIq() = runTest {
        val client = FakePushClient(XmppResult.Success(Unit))
        val service = DefaultXmppPushService(client)

        val result = service.enablePush(
            pushService = Jid(local = null, domain = "push.example.com"),
            node = "node-1",
            requestId = "push-1",
            publishOptions = listOf(XmppPushFormField("secret", "token")),
        )

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:push:0"))
        assertTrue(client.lastStanza!!.contains("<enable"))
    }

    @Test
    fun test_disablePush_whenBlankNode_returnsFailure() = runTest {
        val service = DefaultXmppPushService(FakePushClient(XmppResult.Success(Unit)))

        val result = service.disablePush(
            pushService = Jid(local = null, domain = "push.example.com"),
            node = " ",
            requestId = "push-2",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
    }

    @Test
    fun test_parsePushDirective_whenEnableDirective_returnsParsedDirective() {
        val service = DefaultXmppPushService(FakePushClient(XmppResult.Success(Unit)))
        val xml = "<iq type='set'><enable xmlns='urn:xmpp:push:0' jid='push.example.com' node='n1'/></iq>"

        val parsed = service.parsePushDirective(xml)

        assertIs<XmppResult.Success<ParsedPushDirective>>(parsed)
        assertEquals("enable", parsed.value.action)
        assertEquals("push.example.com", parsed.value.serviceJid)
        assertEquals("n1", parsed.value.node)
    }

    @Test
    fun test_parsePushDirective_whenNamespaceMissing_returnsFailure() {
        val service = DefaultXmppPushService(FakePushClient(XmppResult.Success(Unit)))
        val xml = "<iq><enable jid='push.example.com' node='n1'/></iq>"

        val parsed = service.parsePushDirective(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals(XmppErrorCode.ParsingFailed, parsed.error.code)
    }

    @Test
    fun test_validatePushIqResult_whenMatchingId_returnsSuccess() {
        val service = DefaultXmppPushService(FakePushClient(XmppResult.Success(Unit)))
        val xml = "<iq type='result' id='push-1'/>"

        val validated = service.validatePushIqResult(xml, requestId = "push-1")

        assertIs<XmppResult.Success<Unit>>(validated)
    }
}

private class FakePushClient(private val sendResult: XmppResult<Unit>) : KmpXmppClient {
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
