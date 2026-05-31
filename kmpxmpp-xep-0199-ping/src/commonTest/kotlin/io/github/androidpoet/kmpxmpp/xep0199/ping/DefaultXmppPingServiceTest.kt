package io.github.androidpoet.kmpxmpp.xep0199.ping

import io.github.androidpoet.kmpxmpp.client.KmpXmppClient
import io.github.androidpoet.kmpxmpp.core.Jid
import io.github.androidpoet.kmpxmpp.core.XmppErrorCode
import io.github.androidpoet.kmpxmpp.core.XmppResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultXmppPingServiceTest {
    @Test
    fun test_pingServer_whenValidRequestId_sendsPingIq() = runTest {
        val client = FakePingClient(sendResult = XmppResult.Success(Unit))
        val service = DefaultXmppPingService(client)

        val result = service.pingServer(requestId = "ping-1")

        assertIs<XmppResult.Success<Unit>>(result)
        assertEquals(1, client.sendCalls)
        assertTrue(client.lastStanza!!.contains("urn:xmpp:ping"))
        assertTrue(client.lastStanza!!.contains("id='ping-1'"))
    }

    @Test
    fun test_pingEntity_whenDomainBlank_returnsInvalidInputFailure() = runTest {
        val service = DefaultXmppPingService(FakePingClient(sendResult = XmppResult.Success(Unit)))

        val result = service.pingEntity(
            to = Jid(local = "a", domain = " "),
            requestId = "ping-2",
        )

        assertIs<XmppResult.Failure>(result)
        assertEquals(XmppErrorCode.InvalidInput, result.error.code)
        assertEquals("Target JID domain cannot be blank.", result.error.message)
    }

    @Test
    fun test_parsePingResult_whenValidResultIq_returnsParsedResponse() {
        val service = DefaultXmppPingService(FakePingClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<iq type='result' id='ping-1' from='server.example.com'/>"

        val parsed = service.parsePingResult(xml)

        assertIs<XmppResult.Success<XmppPingResponse>>(parsed)
        assertEquals("ping-1", parsed.value.requestId)
        assertEquals("server.example.com", parsed.value.from?.domain)
    }

    @Test
    fun test_parsePingResult_whenResultIdMissing_returnsFailure() {
        val service = DefaultXmppPingService(FakePingClient(sendResult = XmppResult.Success(Unit)))
        val xml = "<iq type='result'/>"

        val parsed = service.parsePingResult(xml)

        assertIs<XmppResult.Failure>(parsed)
        assertEquals("Ping result stanza missing id attribute.", parsed.error.message)
    }

    @Test
    fun test_isPingTimeoutOrServiceUnavailable_whenTimeoutError_returnsTrue() {
        val service = DefaultXmppPingService(FakePingClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <iq type='error' id='ping-1'>
              <error type='wait'>
                <remote-server-timeout xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
              </error>
            </iq>
        """.trimIndent()

        val result = service.isPingTimeoutOrServiceUnavailable(xml, requestId = "ping-1")

        assertIs<XmppResult.Success<Boolean>>(result)
        assertTrue(result.value)
    }

    @Test
    fun test_isPingTimeoutOrServiceUnavailable_whenDifferentId_returnsFalse() {
        val service = DefaultXmppPingService(FakePingClient(sendResult = XmppResult.Success(Unit)))
        val xml = """
            <iq type='error' id='other-id'>
              <error type='cancel'>
                <service-unavailable xmlns='urn:ietf:params:xml:ns:xmpp-stanzas'/>
              </error>
            </iq>
        """.trimIndent()

        val result = service.isPingTimeoutOrServiceUnavailable(xml, requestId = "ping-1")

        assertIs<XmppResult.Success<Boolean>>(result)
        assertEquals(false, result.value)
    }
}

private class FakePingClient(
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
